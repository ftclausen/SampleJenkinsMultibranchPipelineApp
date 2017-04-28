describe('basic test', function() {
  it('fetches title', function() {
    browser.ignoreSynchronization = true;
    browser.get('http://localhost:3001');

    var title = element(by.xpath('//*[@id="app"]/div/div[1]/div/div[2]/p'));
    expect(title.getText()).toBe('Complex web apps made easy'); 
  });
});
