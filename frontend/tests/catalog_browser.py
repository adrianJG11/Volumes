# The public catalog screen has been replaced by the personal-library home.
from playwright.sync_api import sync_playwright, expect

def check_catalog(url):
    with sync_playwright() as p:
        browser=p.chromium.launch()
        try:
            page=browser.new_page()
            page.route('**/auth/me', lambda route: route.fulfill(json={'id':'reader', 'email':'reader@example.com'}))
            held=[]
            release_failure = False
            def library_response(route):
                # The loading UI may render before the request is intercepted.
                # Also handle late requests from React StrictMode's effect replay.
                if release_failure:
                    route.fulfill(status=503, json={})
                else:
                    held.append(route)
            page.route('**/me/library', library_response)
            page.goto(url)
            expect(page.get_by_text('Loading your library…')).to_be_visible()
            release_failure = True
            for route in held:
                route.fulfill(status=503, json={})
            expect(page.get_by_role('alert')).to_have_text('Could not load your library.')
            page.unroute('**/me/library')
            page.route('**/me/library', lambda route: route.fulfill(json=[]))
            page.get_by_role('button', name='Retry library').click()
            expect(page.get_by_text('Your library is empty.', exact=False)).to_be_visible()
            for response in [{'unexpected':True}, [{'bookId':'bad'}]]:
                page.unroute('**/me/library')
                page.route('**/me/library', lambda route: route.fulfill(json=response))
                page.reload()
                expect(page.get_by_role('alert')).to_have_text('Could not load your library.')
            print('PASS: private library loading, failure/retry, empty and invalid response states')
        finally:
            browser.close()
