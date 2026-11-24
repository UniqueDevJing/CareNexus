import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createSseParser } from './sse.js'

for (const separator of ['\n', '\r\n']) {
  test(`SSE arbitrary chunks ${JSON.stringify(separator)}`, () => {
    const output = []
    const parser = createSseParser(value => output.push(value))
    const wire = `:comment${separator}data: 第一行${separator}data: data:正文${separator}${separator}data:[DONE]`
    for (const char of wire) parser.push(char)
    parser.finish()
    assert.deepEqual(output, ['第一行\ndata:正文', '[DONE]'])
  })
}
