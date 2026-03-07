/**
 * 将表格数据导出为 CSV 文件
 * @param {Array} data - 数据数组
 * @param {Array} columns - 列定义 [{label, key, formatter?}]
 * @param {string} filename - 文件名（不含扩展名）
 */
export function exportToCSV(data, columns, filename = 'export') {
  if (!data || !data.length) return

  const BOM = '\uFEFF'
  const header = columns.map(c => `"${c.label}"`).join(',')
  const rows = data.map(row =>
    columns.map(c => {
      let val = c.formatter ? c.formatter(row[c.key], row) : row[c.key]
      if (val === null || val === undefined) val = ''
      return `"${String(val).replace(/"/g, '""')}"`
    }).join(',')
  )

  const csv = BOM + [header, ...rows].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${filename}_${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
