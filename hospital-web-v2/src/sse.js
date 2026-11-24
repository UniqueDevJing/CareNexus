// 按事件逐行解析；只去掉协议前缀，保留正文中的 data:。
export function createSseParser(onData) {
  let buffer = ''
  function event(block) {
    const lines = block.split(/\r\n|\r|\n/)
    const data = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).replace(/^ /, ''))
    if (data.length) onData(data.join('\n'))
  }
  return {
    push(text) {
      buffer += text
      let boundary
      while ((boundary = /\r\n\r\n|\n\n|\r\r/.exec(buffer))) {
        event(buffer.slice(0, boundary.index))
        buffer = buffer.slice(boundary.index + boundary[0].length)
      }
    },
    finish() { if (buffer) event(buffer); buffer = '' },
  }
}
