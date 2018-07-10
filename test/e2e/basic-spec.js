describe('basic test', () => {
  it('fetches title', () => {
    browser.ignoreSynchronization = true;
    browser.get('http://localhost:3000');

    const title = element(by.xpath('//*[@id="app"]/div/div[1]/div/div[2]/p'));
    expect(title.getText()).toBe('Complex web apps made easy');
  });
});
