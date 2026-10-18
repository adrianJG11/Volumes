"""Run with the isolated Playwright Python interpreter and Vite already running."""
import argparse
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
