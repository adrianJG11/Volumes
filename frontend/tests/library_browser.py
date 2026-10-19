import base64
import os
import uuid
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

def check_library(url):
    password='library browser passphrase'
    title='The Brothers Karamazov'
    with sync_playwright() as p:
        browser=p.chromium.launch()
        try:
            pages=[]
            book_id=None
            for index in range(2):
                page=browser.new_page(viewport={'width':390 if index == 0 else 1440, 'height':950})
                email='library-' + uuid.uuid4().hex + '@example.com'
                page.route('https://covers.openlibrary.org/**', lambda route: route.fulfill(content_type='image/png', body=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=')))
                page.goto(url + '/register')
                page.get_by_label('Email', exact=True).fill(email)
                page.get_by_label('Password', exact=True).fill(password)
                page.get_by_role('button', name='Create account', exact=True).click()
                expect(page).to_have_url(url + '/login?registered=1')
                page.get_by_label('Email', exact=True).fill(email)
                page.get_by_label('Password', exact=True).fill(password)
                page.get_by_role('button', name='Sign in', exact=True).click()
                expect(page).to_have_url(url + '/')
                expect(page.get_by_text('Your library is empty.', exact=False)).to_be_visible()
                if index == 0:
                    page.route('**/books/search?*', lambda route: route.fulfill(json=[{
                        'title':title,'author':'Fyodor Dostoevsky','genre':'Fiction','pages':1013,
                        'isbn':'9780140449242','coverUrl':None,'firstPublishYear':1880}]))
                    page.get_by_role('button', name='Add a book', exact=True).click()
                    page.get_by_label('Title', exact=True).fill('Karamazov')
                    page.get_by_role('list', name='Matching books').get_by_role('button').click()
                    expect(page.get_by_label('Author', exact=True)).to_have_value('Fyodor Dostoevsky')
                    page.get_by_label('Reading status', exact=True).select_option('READING')
                    # A failed personal-library write can be retried without creating another shared book.
                    page.route('**/me/library', lambda route: route.fulfill(status=503, json={}) if route.request.method == 'POST' else route.continue_())
                    page.get_by_role('button', name='Add to my library', exact=True).click()
                    expect(page.get_by_role('alert')).to_contain_text('Retry to continue with the same book')
                    before=page.request.get(url + '/books').json()
                    page.unroute('**/me/library')
                    page.get_by_role('button', name='Retry adding to library').click()
                    expect(page.get_by_role('heading', name=title, exact=True)).to_be_visible()
                    assert len(page.request.get(url + '/books').json()) == len(before)
                    book_id=page.request.get(url + '/me/library').json()[0]['bookId']
                    expect(page.get_by_label('Reading status for ' + title, exact=True)).to_have_value('READING')
                    expect(page.locator('li img')).to_have_count(1)
                else:
                    # Seed the SAME shared book for the second reader to verify ownership.
                    csrf=page.request.get(url + '/auth/csrf').json()
                    assert page.request.post(url + '/me/library', data={'bookId':book_id}, headers={csrf['headerName']:csrf['token']}).status == 201
                    page.reload()
                rating=page.get_by_label('Your rating for ' + title, exact=True)
                rating.select_option(str(5-index*3))
                page.get_by_role('button', name='Save rating', exact=True).click()
                expect(page.get_by_text('Rating saved.', exact=True)).to_be_visible()
                page.reload()
                expect(page.get_by_text('Saved rating: '+str(5-index*3)+' / 5')).to_be_visible()
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
                pages.append(page)
            alice,bob=pages
            alice.get_by_label('Reading status for '+title,exact=True).select_option('FINISHED')
            expect(alice.get_by_label('Reading status for '+title,exact=True)).to_be_enabled()
            alice.reload()
            expect(alice.get_by_label('Reading status for '+title,exact=True)).to_have_value('FINISHED')
            bob.reload()
            expect(bob.get_by_label('Reading status for '+title,exact=True)).to_have_value('PENDING')
            expect(bob.get_by_text('Saved rating: 2 / 5')).to_be_visible()
            alice.route('**/rating',lambda route:route.fulfill(status=503,json={}))
            alice.get_by_label('Your rating for '+title,exact=True).select_option('4')
            alice.get_by_role('button',name='Save rating',exact=True).click()
            expect(alice.get_by_role('alert')).to_contain_text('saved rating has not changed')
            expect(alice.get_by_text('Saved rating: 5 / 5')).to_be_visible()
            alice.unroute('**/rating')
            alice.get_by_role('button',name='Save rating',exact=True).click()
            expect(alice.get_by_text('Saved rating: 4 / 5')).to_be_visible()
            alice.get_by_role('button',name='Clear rating',exact=True).click()
            expect(alice.get_by_text('Rating cleared.',exact=True)).to_be_visible()
            alice.reload()
            expect(alice.get_by_text('Saved rating: Not rated')).to_be_visible()
            alice.get_by_role('button',name='Edit ISBN for '+title,exact=True).click()
            alice.get_by_role('button',name='Remove ISBN',exact=True).click()
            expect(alice.get_by_text('ISBN and cover removed. The book is still in your library.')).to_be_visible()
            alice.reload()
            expect(alice.locator('li img')).to_have_count(0)
            expect(alice.get_by_role('heading',name=title,exact=True)).to_be_visible()
            output=Path(os.environ.get('VOLUMES_SCREENSHOTS','/tmp/volumes-personal-flow'))
            output.mkdir(parents=True,exist_ok=True)
            for width in [390,1440]:
                alice.set_viewport_size({'width':width,'height':950})
                alice.screenshot(path=str(output/('library-'+str(width)+'.png')),full_page=True)
                assert alice.evaluate('document.documentElement.scrollWidth <= innerWidth')
            alice.context.clear_cookies()
            alice.get_by_label('Your rating for '+title,exact=True).select_option('3')
            alice.get_by_role('button',name='Save rating',exact=True).click()
            expect(alice).to_have_url(url+'/login')
            expect(alice.get_by_role('heading',name=title,exact=True)).to_have_count(0)
            bob.get_by_role('button',name='Sign out',exact=True).click()
            expect(bob).to_have_url(url+'/login')
            print('PASS: search/pick cover, direct add and retry, reading status, independent ratings, reload, cover removal and expiry')
        finally:
            browser.close()
