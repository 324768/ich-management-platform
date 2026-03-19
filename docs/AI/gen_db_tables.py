import pymysql
import os

conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='123456', charset='utf8mb4')
cursor = conn.cursor()

databases = ['user_center', 'content_center', 'order_center', 'product_center', 'ai_service', 'system_manage', 'common_db']
db_names_cn = {
    'user_center': '用户中心库',
    'content_center': '内容中心库',
    'order_center': '订单中心库',
    'product_center': '商品中心库',
    'ai_service': 'AI服务库',
    'system_manage': '系统管理库',
    'common_db': '公共基础库'
}

# Get all table info
all_tables = {}
for db in databases:
    cursor.execute("""
        SELECT TABLE_NAME, TABLE_COMMENT 
        FROM information_schema.TABLES 
        WHERE TABLE_SCHEMA=%s AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME != 'undo_log'
        ORDER BY TABLE_NAME
    """, (db,))
    tables = cursor.fetchall()
    all_tables[db] = []
    for tname, tcomment in tables:
        cursor.execute("""
            SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, COLUMN_COMMENT
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s
            ORDER BY ORDINAL_POSITION
        """, (db, tname))
        cols = cursor.fetchall()
        all_tables[db].append((tname, tcomment, cols))

# Generate HTML
html = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>数据库表结构文档</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'微软雅黑','宋体',sans-serif;background:#f5f5f5;padding:20px;max-width:1100px;margin:0 auto}
h1{text-align:center;margin:20px 0;font-size:22px}
h2{background:#333;color:#fff;padding:10px 15px;margin:30px 0 0;font-size:16px;border-radius:4px 4px 0 0}
h3{background:#e8e8e8;padding:8px 15px;margin:15px 0 0;font-size:14px;border:1px solid #ccc;border-bottom:none}
.toc{background:#fff;border:1px solid #ddd;padding:15px 20px;margin:15px 0 30px;border-radius:4px}
.toc h3{background:none;border:none;margin:0 0 10px;font-size:15px;padding:0}
.toc ul{list-style:none;padding-left:0}
.toc li{padding:2px 0;font-size:13px}
.toc .db-name{font-weight:bold;margin-top:8px;font-size:14px;color:#333}
table{width:100%;border-collapse:collapse;background:#fff;margin-bottom:15px}
table th{background:#f0f0f0;padding:6px 10px;border:1px solid #ccc;font-size:12px;text-align:left;white-space:nowrap}
table td{padding:5px 10px;border:1px solid #ccc;font-size:12px}
table tr:hover{background:#f9f9f9}
.pk{color:#c00;font-weight:bold}
.summary{background:#fff;border:1px solid #ddd;padding:10px 15px;margin:10px 0 25px;font-size:13px;border-radius:4px}
.summary span{display:inline-block;margin-right:20px}
</style>
</head>
<body>
<h1>非遗文化管理平台 — 数据库表结构文档</h1>
<div class="summary">
"""

# Summary
total_tables = sum(len(v) for v in all_tables.values())
total_cols = sum(sum(len(cols) for _, _, cols in v) for v in all_tables.values())
html += f'<span><b>数据库数量：</b>{len(databases)}个</span>'
html += f'<span><b>数据表总数：</b>{total_tables}张</span>'
html += f'<span><b>字段总数：</b>{total_cols}个</span>'
html += '</div>\n'

# TOC
html += '<div class="toc"><h3>目录</h3>\n'
for db in databases:
    html += f'<p class="db-name">📁 {db_names_cn[db]}（{db}）</p><ul>\n'
    for tname, tcomment, cols in all_tables[db]:
        comment_str = f' — {tcomment}' if tcomment else ''
        html += f'<li><a href="#{db}_{tname}">{tname}</a>{comment_str}（{len(cols)}字段）</li>\n'
    html += '</ul>\n'
html += '</div>\n'

# Tables
for db in databases:
    html += f'<h2>📁 {db_names_cn[db]}（{db}）— 共{len(all_tables[db])}张表</h2>\n'
    for tname, tcomment, cols in all_tables[db]:
        comment_str = f' — {tcomment}' if tcomment else ''
        html += f'<h3 id="{db}_{tname}">{tname}{comment_str}</h3>\n'
        html += '<table><tr><th>序号</th><th>字段名</th><th>类型</th><th>允许空</th><th>键</th><th>默认值</th><th>说明</th></tr>\n'
        for i, (cname, ctype, nullable, key, default, comment) in enumerate(cols, 1):
            key_cls = ' class="pk"' if key == 'PRI' else ''
            key_str = key if key else ''
            default_str = str(default) if default is not None else ''
            nullable_str = '是' if nullable == 'YES' else '否'
            comment_str = comment if comment else ''
            html += f'<tr><td>{i}</td><td{key_cls}>{cname}</td><td>{ctype}</td><td>{nullable_str}</td><td>{key_str}</td><td>{default_str}</td><td>{comment_str}</td></tr>\n'
        html += '</table>\n'

html += '<p style="text-align:center;color:#999;margin:30px 0;font-size:12px">自动生成于 MySQL 8.0 information_schema</p>\n'
html += '</body></html>'

output_path = os.path.join(os.path.dirname(__file__), 'db_tables.html')
with open(output_path, 'w', encoding='utf-8') as f:
    f.write(html)

cursor.close()
conn.close()
print(f'Done! Generated {total_tables} tables, {total_cols} columns -> {output_path}')
