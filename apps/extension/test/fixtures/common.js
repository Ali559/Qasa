// Fixture helper: forms don't navigate; their values are written to #result as JSON so
// the tests can read what the page itself received.
function qasaFixtureWire() {
  for (const form of document.querySelectorAll('form[data-record]')) {
    form.addEventListener('submit', (e) => {
      e.preventDefault()
      const values = {}
      for (const input of form.querySelectorAll('input')) values[input.name || input.id] = input.value
      document.getElementById('result').textContent = JSON.stringify(values)
      const next = form.dataset.next
      if (next) setTimeout(() => (location.href = next), 50)
    })
  }
}
document.addEventListener('DOMContentLoaded', qasaFixtureWire)
