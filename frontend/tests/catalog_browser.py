"""Run with the isolated Playwright Python interpreter and Vite already running."""
import argparse
import base64
from playwright.sync_api import sync_playwright, expect

BOOKS = [
    {"id": "first", "title": "The Brothers Karamazov", "author": "Fyodor Dostoevsky", "genre": "Fiction", "pages": 800},
    {"id": "second", "title": "The Count of Monte Cristo", "author": "Alexandre Dumas", "genre": "Fiction", "pages": 1000},
]


def check_catalog(base_url, real_api=False, screenshot_dir=None):
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        try:
            errors = []
            def new_page():
                page = browser.new_page()
                page.route("**/auth/me", lambda route: route.fulfill(status=401, json={"status":401}))
                page.on("pageerror", lambda error: errors.append(str(error)))
                return page

            page = new_page()
            pending = []
            page.route("**/books", lambda route: pending.append(route))
            page.goto(base_url)
            expect(page.get_by_role("status")).to_have_text("Loading books…")
            expect(page.get_by_role("button", name="Try again")).to_have_count(0)
            for route in pending:
                route.fulfill(json=[])
            expect(page.get_by_role("status")).to_have_text("No books in the catalog yet.")
            page.close()
            print("PASS: pending request shows loading, not an empty catalog")

            page = new_page()
            page.route("**/books", lambda route: route.fulfill(json=BOOKS))
            for width in [1440, 390]:
                page.set_viewport_size({"width": width, "height": 900})
                page.goto(base_url)
                expect(page.get_by_role("heading", name=BOOKS[0]["title"], exact=True)).to_be_visible()
                expect(page.get_by_role("listitem")).to_have_count(2)
                assert page.get_by_role("heading", level=3).all_text_contents() == [b["title"] for b in BOOKS]
                expect(page.get_by_text("Fyodor Dostoevsky", exact=True)).to_be_visible()
                expect(page.get_by_text("800 pages", exact=True)).to_be_visible()
                expect(page.get_by_text("1,000 pages", exact=True)).to_be_visible()
                assert page.evaluate("document.documentElement.scrollWidth <= innerWidth")
                if screenshot_dir:
                    page.screenshot(path=f"{screenshot_dir}/catalog-{width}.png", full_page=True)
            page.close()
            print("PASS: catalog fields, insertion order, desktop/mobile layout")

            for name, handler in [
                ("HTTP failure", lambda route: route.fulfill(status=503, body="Unavailable")),
                ("network failure", lambda route: route.abort()),
                ("invalid JSON", lambda route: route.fulfill(content_type="application/json", body="{")),
                ("wrong response shape", lambda route: route.fulfill(json=[{"title": "Incomplete"}])),
            ]:
                page = new_page()
                page.route("**/books", handler)
                page.goto(base_url)
                expect(page.get_by_role("alert")).to_be_visible()
                page.unroute("**/books")
                page.route("**/books", lambda route: route.fulfill(json=BOOKS))
                page.get_by_role("button", name="Try again").click()
                expect(page.get_by_role("heading", name=BOOKS[0]["title"], exact=True)).to_be_visible()
                expect(page.get_by_role("alert")).to_have_count(0)
                page.close()
                print(f"PASS: {name} and successful retry")

            page = new_page()
            page.route("**/books", lambda route: route.fulfill(json=[]))
            page.goto(base_url)
            expect(page.get_by_role("status")).to_have_text("No books in the catalog yet.")
            page.unroute("**/books")
            hostile = dict(BOOKS[0], title="<script>window.catalogInjected=true</script>" + "LongTitle" * 25)
            page.route("**/books", lambda route: route.fulfill(json=[hostile]))
            page.set_viewport_size({"width": 320, "height": 800})
            page.get_by_role("button", name="Refresh catalog").click()
            expect(page.get_by_role("heading", name=hostile["title"], exact=True)).to_be_visible()
            assert page.evaluate("window.catalogInjected === undefined")
            assert page.evaluate("document.documentElement.scrollWidth <= innerWidth")
            page.close()
            print("PASS: empty state refresh, escaped text, long title at 320px")

            # Covers use a deterministic local image response; tests never need Open Library.
            edition = {"isbn": "9780140449242", "openLibraryId": "OL3701480M",
                       "coverUrl": "https://covers.openlibrary.org/b/id/8457009-M.jpg?default=false",
                       "sourceUrl": "https://openlibrary.org/books/OL3701480M"}
            covered = dict(BOOKS[0], edition=edition)
            for succeeds in [True, False]:
                page = new_page()
                page.route("**/books", lambda route: route.fulfill(json=[covered, BOOKS[1]]))
                def cover_response(route):
                    if succeeds:
                        route.fulfill(content_type="image/png", body=base64.b64decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="))
                    else:
                        route.fulfill(status=404, body="No cover")
                page.route("https://covers.openlibrary.org/**", cover_response)
                page.set_viewport_size({"width": 390, "height": 900})
                page.goto(base_url)
                expect(page.get_by_role("link", name="View the edition", exact=False)).to_have_attribute("href", edition["sourceUrl"])
                if succeeds:
                    page.wait_for_function("document.querySelector('li img')?.naturalWidth > 0")
                    expect(page.locator("li").nth(1).get_by_text("No cover")).to_be_visible()
                else:
                    expect(page.locator("li").first.get_by_text("No cover")).to_be_visible()
                    expect(page.locator("li img")).to_have_count(0)
                assert page.evaluate("document.documentElement.scrollWidth <= innerWidth")
                page.close()
            print("PASS: edition attribution, loaded cover, missing cover and failed image fallback")

            page = new_page()
            created = []
            suggestion = {"title": "The Hobbit", "author": "J.R.R. Tolkien", "genre": "Fantasy", "pages": 310,
                          "isbn": "9780395520215", "coverUrl": None, "firstPublishYear": 1937}
            searches = []
            def books_route(route):
                if route.request.method == "POST":
                    assert route.request.headers["x-csrf-token"] == "token"
                    created.append(dict(route.request.post_data_json, id="new"))
                    route.fulfill(status=201, json=created[-1])
                else:
                    route.fulfill(json=[])
            def search_route(route):
                searches.append(route.request.url)
                route.fulfill(json=[suggestion] if "hobbit" in route.request.url else [])
            edition = {"isbn": "9780395520215", "openLibraryId": None, "coverUrl": "https://is1-ssl.mzstatic.com/image/thumb/a/600x600bb.jpg",
                       "sourceUrl": None}
            page.route("**/auth/me", lambda route: route.fulfill(json={"id": "reader", "email": "reader@example.com"}))
            page.route("**/auth/csrf", lambda route: route.fulfill(json={"token": "token", "headerName": "X-CSRF-TOKEN", "parameterName": "_csrf"}))
            page.route("**/books", books_route)
            page.route("**/books/search?*", search_route)
            page.route("**/books/new/edition", lambda route: route.fulfill(json=dict(created[-1], edition=edition)))
            covers = []
            page.route("https://is1-ssl.mzstatic.com/**", lambda route: (covers.append(route.request.url), route.fulfill(status=404)))
            page.goto(base_url)
            page.get_by_role("button", name="Add a book").click()
            page.get_by_label("Title").press_sequentially("ho", delay=20)
            page.wait_for_timeout(900)
            assert searches == [], "titles shorter than three characters must not search"
            page.get_by_label("Title").press_sequentially("bbit", delay=20)
            page.get_by_role("list", name="Matching books").get_by_role("button").click()
            assert len(searches) == 1, searches
            assert [page.locator(f"#new-book-{name}").input_value() for name in ["title", "author", "genre", "pages", "isbn"]] \
                == ["The Hobbit", "J.R.R. Tolkien", "Fantasy", "310", "9780395520215"]
            page.get_by_label("Genre").fill("Classic fantasy")
            page.get_by_role("button", name="Add to catalog").click()
            expect(page.get_by_text("“The Hobbit” was added to the catalog.")).to_be_visible()
            assert created == [{"title": "The Hobbit", "author": "J.R.R. Tolkien", "genre": "Classic fantasy", "pages": 310, "id": "new"}]
            expect(page.get_by_role("heading", level=3)).to_have_text(["The Hobbit"])
            expect(page.get_by_role("button", name="Edit ISBN for The Hobbit")).to_be_visible()
            # An ISBN Open Library does not know still shows its cover (here from iTunes), without the Open Library link.
            expect(page.get_by_role("link", name="View the edition", exact=False)).to_have_count(0)
            # BookCover drops the <img> once the (intercepted) image fails, so by then it was requested.
            expect(page.locator("li img")).to_have_count(0)
            assert covers == [edition["coverUrl"]], covers
            page.close()
            print("PASS: add book searches after three characters, prefills editable fields, creates and attaches ISBN")

            if real_api:
                page = new_page()
                response = page.request.get(base_url + "/books")
                assert response.ok
                books = response.json()
                assert books, "Seed an isolated real catalog before this check"
                page.goto(base_url)
                expect(page.get_by_role("listitem")).to_have_count(len(books))
                assert page.get_by_role("heading", level=3).all_text_contents() == [b["title"] for b in books]
                page.close()
                print("PASS: actual Java API through Vite proxy, no browser request mocks")
            assert not errors, errors
        finally:
            browser.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:5173")
    parser.add_argument("--real-api", action="store_true")
    parser.add_argument("--screenshots")
    args = parser.parse_args()
    check_catalog(args.url, args.real_api, args.screenshots)
