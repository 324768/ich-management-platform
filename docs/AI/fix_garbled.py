#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Fix garbled Chinese text in sys_user and sys_user_role tables"""

filepath = r'd:\ich-management-platform\docs\AI\db_tables.html'

with open(filepath, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
i = 0
while i < len(lines):
    line = lines[i]

    # Fix sys_user heading (not sys_user_message, not sys_user_role)
    if 'id="system_manage_sys_user"' in line and 'sys_user_role' not in line and 'sys_user_message' not in line:
        new_lines.append('<h3 id="system_manage_sys_user">sys_user — 系统用户表</h3>\n')
        i += 1
        # Next line is <table> header, keep it
        new_lines.append(lines[i])
        i += 1
        # Now replace the 13 data rows
        replacements = [
            '<tr><td>1</td><td class="pk">id</td><td>bigint</td><td>否</td><td>PRI</td><td></td><td>用户ID</td></tr>\n',
            '<tr><td>2</td><td>username</td><td>varchar(50)</td><td>否</td><td>UNI</td><td></td><td>用户名</td></tr>\n',
            '<tr><td>3</td><td>password</td><td>varchar(100)</td><td>否</td><td></td><td></td><td>密码</td></tr>\n',
            '<tr><td>4</td><td>nickname</td><td>varchar(50)</td><td>是</td><td></td><td></td><td>昵称</td></tr>\n',
            '<tr><td>5</td><td>avatar</td><td>longtext</td><td>是</td><td></td><td></td><td>头像（Base64存储）</td></tr>\n',
            '<tr><td>6</td><td>email</td><td>varchar(100)</td><td>是</td><td></td><td></td><td>邮箱</td></tr>\n',
            '<tr><td>7</td><td>phone</td><td>varchar(20)</td><td>是</td><td></td><td></td><td>手机号</td></tr>\n',
            '<tr><td>8</td><td>status</td><td>tinyint(1)</td><td>是</td><td></td><td>1</td><td>状态：0-禁用，1-启用</td></tr>\n',
            '<tr><td>9</td><td>last_login_time</td><td>datetime</td><td>是</td><td></td><td></td><td>最后登录时间</td></tr>\n',
            '<tr><td>10</td><td>last_login_ip</td><td>varchar(50)</td><td>是</td><td></td><td></td><td>最后登录IP</td></tr>\n',
            '<tr><td>11</td><td>create_time</td><td>datetime</td><td>否</td><td></td><td>CURRENT_TIMESTAMP</td><td>创建时间</td></tr>\n',
            '<tr><td>12</td><td>update_time</td><td>datetime</td><td>否</td><td></td><td>CURRENT_TIMESTAMP</td><td>更新时间</td></tr>\n',
            '<tr><td>13</td><td>is_deleted</td><td>tinyint(1)</td><td>是</td><td></td><td>0</td><td>是否删除：0-未删除，1-已删除</td></tr>\n',
        ]
        for r in replacements:
            new_lines.append(r)
            i += 1  # skip original garbled line
        continue

    # Fix sys_user_role heading
    if 'id="system_manage_sys_user_role"' in line:
        new_lines.append('<h3 id="system_manage_sys_user_role">sys_user_role — 用户角色关联表</h3>\n')
        i += 1
        # Next line is <table> header, keep it
        new_lines.append(lines[i])
        i += 1
        # Replace 4 data rows
        replacements = [
            '<tr><td>1</td><td class="pk">id</td><td>bigint</td><td>否</td><td>PRI</td><td></td><td>主键ID</td></tr>\n',
            '<tr><td>2</td><td>user_id</td><td>bigint</td><td>否</td><td>MUL</td><td></td><td>用户ID</td></tr>\n',
            '<tr><td>3</td><td>role_id</td><td>bigint</td><td>否</td><td></td><td></td><td>角色ID</td></tr>\n',
            '<tr><td>4</td><td>create_time</td><td>datetime</td><td>否</td><td></td><td>CURRENT_TIMESTAMP</td><td>创建时间</td></tr>\n',
        ]
        for r in replacements:
            new_lines.append(r)
            i += 1  # skip original garbled line
        continue

    new_lines.append(line)
    i += 1

with open(filepath, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

print("Done! Fixed sys_user and sys_user_role tables.")
