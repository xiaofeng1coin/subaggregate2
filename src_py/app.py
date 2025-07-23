# 文件名: app.py (最终决定版 - 包含调试功能和详细日志)

import os
import json
import logging
import base64
import yaml
import binascii
import time
import io
from urllib.parse import urlparse, parse_qs, unquote
from flask import Flask, request, jsonify, make_response, render_template
from markupsafe import escape
import requests

# --- 基础设置 ---
app = Flask(__name__, static_folder='static', template_folder='templates')

# --- 日志系统设置 (无改动) ---
log_capture_string = io.StringIO()
root_logger = logging.getLogger()
root_logger.setLevel(logging.INFO)
log_handler = logging.StreamHandler(log_capture_string)
formatter = logging.Formatter('%(asctime)s | %(levelname)-7s | %(message)s', datefmt='%Y-%m-%d %H:%M:%S')
log_handler.setFormatter(formatter)
root_logger.addHandler(log_handler)
console_handler = logging.StreamHandler()
console_handler.setFormatter(formatter)
root_logger.addHandler(console_handler)


# --- 文件路径定位 (无改动) ---
try:
    from com.chaquo.python.android import AndroidPlatform
    IN_ANDROID = True
except ImportError:
    IN_ANDROID = False

if IN_ANDROID:
    current_script_path = os.path.abspath(__file__)
    app_dir = os.path.dirname(current_script_path)
    assetfinder_dir = os.path.dirname(app_dir)
    chaquopy_dir = os.path.dirname(assetfinder_dir)
    writable_files_dir = os.path.dirname(chaquopy_dir)
    DATA_FILE = os.path.join(writable_files_dir, 'data.json')
    CLASH_TEMPLATE_FILE = os.path.join(app_dir, 'clash_template.yaml')
else:
    current_script_dir = os.path.dirname(os.path.abspath(__file__))
    DATA_FILE = os.path.join(current_script_dir, 'data.json')
    CLASH_TEMPLATE_FILE = os.path.join(current_script_dir, 'clash_template.yaml')

logging.info("=" * 50)
logging.info(f"DIAGNOSTIC: Environment detected: {'Android' if IN_ANDROID else 'Docker/Local'}")
logging.info(f"DIAGNOSTIC: DATA_FILE path is set to -----> {DATA_FILE}")
logging.info(f"DIAGNOSTIC: CLASH_TEMPLATE_FILE path is set to -----> {CLASH_TEMPLATE_FILE}")
logging.info("=" * 50)


# =================================================================================
# 节点解析 (Parsers) - (无改动)
# =================================================================================

def parse_link(link: str):
    try:
        if link.startswith('ss://'): return _parse_ss(link)
        if link.startswith('vmess://'): return _parse_vmess(link)
        if link.startswith('trojan://'): return _parse_trojan(link)
    except Exception:
        return None
    return None


def _parse_ss(link: str):
    try:
        main_part, name_part = link[5:].split('#', 1)
        name = unquote(name_part.strip())
        if '@' in main_part:
            encoded_credentials, server_info = main_part.split('@', 1)
            server, port = server_info.rsplit(':', 1)
            try:
                padding = len(encoded_credentials) % 4
                if padding: encoded_credentials += '=' * (4 - padding)
                credentials = base64.b64decode(encoded_credentials).decode('utf-8')
                cipher, password = credentials.split(':', 1)
            except (binascii.Error, ValueError):
                credentials = unquote(encoded_credentials)
                cipher, password = credentials.split(':', 1)
            return {'name': name, 'type': 'ss', 'server': server.strip('[]'), 'port': int(port), 'cipher': cipher,
                    'password': password}
    except Exception:
        return None


def _parse_vmess(link: str):
    try:
        encoded_data = link[8:]
        padding = len(encoded_data) % 4
        if padding: encoded_data += '=' * (4 - padding)
        vmess_data = json.loads(base64.b64decode(encoded_data).decode('utf-8'))
        ws_opts = {}
        if vmess_data.get('net') == 'ws':
            host = vmess_data.get('host', '').strip() or vmess_data.get('add')
            ws_opts = {'path': vmess_data.get('path', '/'), 'headers': {'Host': host}}
        return {'name': vmess_data.get('ps'), 'type': 'vmess', 'server': vmess_data.get('add'),
                'port': int(vmess_data.get('port')), 'uuid': vmess_data.get('id'),
                'alterId': int(vmess_data.get('aid')), 'cipher': vmess_data.get('scy', 'auto'),
                'tls': vmess_data.get('tls') == 'tls', 'network': vmess_data.get('net'),
                'ws-opts': ws_opts if ws_opts else None, 'servername': vmess_data.get('sni') or (
                vmess_data.get('host') if vmess_data.get('tls') == 'tls' else None)}
    except Exception:
        return None


def _parse_trojan(link: str):
    try:
        parsed_url = urlparse(link)
        params = parse_qs(parsed_url.query)
        name = unquote(parsed_url.fragment)
        sni = params.get('sni', [None])[0] or params.get('peer', [None])[0] or parsed_url.hostname
        return {'name': name, 'type': 'trojan', 'server': parsed_url.hostname, 'port': parsed_url.port,
                'password': parsed_url.username, 'sni': sni,
                'alpn': params.get('alpn', [None])[0].split(',') if params.get('alpn') else None,
                'skip-cert-verify': params.get('allowInsecure', ['0'])[0] in ['1', 'true']}
    except Exception:
        return None


# =================================================================================
# Clash配置生成 (Generator) - (无改动)
# =================================================================================

def generate_clash_config(proxies: list, template_content: str) -> str:
    logging.info("--- [开始生成Clash配置] ---")
    try:
        config_dict = yaml.safe_load(template_content)
        if not isinstance(config_dict, dict):
            raise yaml.YAMLError("模板文件不是一个有效的YAML字典。")
    except yaml.YAMLError as e:
        logging.error(f"  [错误] 解析YAML模板时出错: {e}")
        return "# 模板解析失败，请检查 clash_template.yaml 文件格式。"
    junk_keywords = ['流量', '到期', '重置', '过滤', '剩余', '套餐']
    filtered_proxies = [p for p in proxies if not any(kw in p.get('name', '').lower() for kw in junk_keywords)]
    logging.info(f"  - [步骤1] 过滤流量信息等无效条目，有效节点数: {len(proxies)} -> {len(filtered_proxies)}")
    config_dict['proxies'] = filtered_proxies
    all_proxy_names = [p['name'] for p in filtered_proxies]
    logging.info("  - [步骤2] 将所有有效节点注入到模板 'proxies' 键。")
    logging.info("  - [步骤3] 开始智能地区分组...")
    region_map = {
        '🇭🇰 香港节点': ['香港', 'Hong Kong', 'HK', 'HKT'], '🇨🇳 台湾节点': ['台湾', 'Taiwan', 'TW', 'TPE'],
        '🇸🇬 狮城节点': ['新加坡', 'Singapore', 'SG'], '🇯🇵 日本节点': ['日本', 'Japan', 'JP', 'TYO'],
        '🇺🇲 美国节点': ['美国', 'United States', 'US'], '🇰🇷 韩国节点': ['韩国', 'Korea', 'KR'],
        '🇬🇧 英国节点': ['英国', 'England', 'UK'], '🇩🇪 德国节点': ['德国', 'Germany', 'DE'],
        '🇫🇷 法国节点': ['法国', 'France', 'FR'], '🇳🇱 荷兰节点': ['荷兰', 'Netherlands', 'NL'],
        '🇨🇦 加拿大节点': ['加拿大', 'Canada', 'CA'], '🇦🇺 澳洲节点': ['澳大利亚', 'Australia', 'AU'],
        '🇷🇺 俄国节点': ['俄罗斯', 'Russia', 'RU'], '🇦🇪 阿联酋节点': ['阿联酋', 'AE'],
        '🇮🇳 印度节点': ['印度', 'India', 'IN'], '🇻🇳 越南节点': ['越南', 'Vietnam', 'VN'],
        '🇵🇱 波兰节点': ['波兰', 'Poland', 'PL']
    }
    region_nodes = {key: [] for key in region_map.keys()}
    other_nodes = []
    for name in all_proxy_names:
        matched = False
        for region_name_emoji, keywords in region_map.items():
            if any(keyword.lower() in name.lower() for keyword in keywords):
                region_nodes[region_name_emoji].append(name)
                matched = True
                break
        if not matched:
            other_nodes.append(name)
    logging.info("    - 分组统计报告:")
    for region, nodes in region_nodes.items():
        if nodes: logging.info(f"      - {region}: {len(nodes)} 个节点")
    if other_nodes:
        logging.warning(f"    - 注意: {len(other_nodes)} 个节点被分入 '🌍 其他地区'。")
        for i, node_name in enumerate(other_nodes[:10]):
            logging.info(f"      - 其他地区节点示例: {node_name}")
        if len(other_nodes) > 10:
            logging.info(f"      - ... (还有 {len(other_nodes) - 10} 个未显示)")
    logging.info("  - [步骤4] 填充模板中的代理组...")
    if 'proxy-groups' in config_dict and isinstance(config_dict['proxy-groups'], list):
        for group in config_dict['proxy-groups']:
            if isinstance(group, dict) and 'name' in group:
                group_name = group.get('name', '')
                if group_name in ['🚀 手动切换', '♻️ 自动选择', '🔯 故障转移', '🔮 负载均衡']:
                    group['proxies'] = all_proxy_names
                    logging.info(f"    - 已填充通用组 '{group_name}'，共 {len(all_proxy_names)} 个节点。")
                elif group_name in region_nodes:
                    nodes_to_fill = region_nodes[group_name]
                    group['proxies'] = nodes_to_fill if nodes_to_fill else ['DIRECT']
                    logging.info(f"    - 已填充地区组 '{group_name}'，共 {len(nodes_to_fill)} 个节点。")
                elif group_name == '🌍 其他地区':
                    group['proxies'] = other_nodes if other_nodes else ['DIRECT']
                    logging.info(f"    - 已填充组 '{group_name}'，共 {len(other_nodes)} 个节点。")
    logging.info("--- [Clash配置生成完毕] ---")
    return yaml.dump(config_dict, allow_unicode=True, sort_keys=False)


# =================================================================================
# 辅助函数 - (无改动)
# =================================================================================

def load_data():
    logging.info(f"DIAGNOSTIC: Attempting to LOAD data from {DATA_FILE}")
    if not os.path.exists(DATA_FILE):
        logging.warning(f"DIAGNOSTIC: File not found at {DATA_FILE}. Returning empty default data.")
        return {"subscriptions": [], "aggregation_enabled": [], "global_filter_enabled": False,
                "global_filter_keywords": ""}
    try:
        with open(DATA_FILE, 'r', encoding='utf-8') as f:
            data = json.load(f)
            logging.info(f"DIAGNOSTIC: Successfully LOADED data. Subscription count: {len(data.get('subscriptions',[]))}")
            return data
    except Exception as e:
        logging.error(f"DIAGNOSTIC: !!! FAILED to LOAD or PARSE data from {DATA_FILE}: {e}", exc_info=True)
        return {"subscriptions": [], "aggregation_enabled": [], "global_filter_enabled": False,
                "global_filter_keywords": ""}


def save_data(data):
    logging.info(f"DIAGNOSTIC: Attempting to SAVE data to {DATA_FILE}")
    try:
        with open(DATA_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=4, ensure_ascii=False)
        logging.info(f"DIAGNOSTIC: Successfully SAVED data. File should now exist at {DATA_FILE}")
        if os.path.exists(DATA_FILE):
            logging.info(f"DIAGNOSTIC: VERIFIED - File exists after save.")
        else:
            logging.error(f"DIAGNOSTIC: !!! CRITICAL FAILURE - File DOES NOT exist after supposedly successful save!")
    except Exception as e:
        logging.error(f"DIAGNOSTIC: !!! FAILED to SAVE data to {DATA_FILE}: {e}", exc_info=True)


def apply_filter(nodes, keywords_str, filter_type):
    if not keywords_str:
        return nodes
    keywords = []
    if filter_type == "全局":
        keywords = [kw for kw in keywords_str.split(' ') if kw]
    else:
        standardized_str = keywords_str.replace('\n', ',').replace(' ', ',')
        keywords = [kw.strip() for kw in standardized_str.split(',') if kw.strip()]
    if not keywords:
        return nodes
    logging.info(f"    - 执行 {filter_type} 过滤, 关键词 (大小写敏感): {', '.join(keywords)}")
    original_count = len(nodes)
    filtered_nodes = [node for node in nodes if not any(kw in node.get('name', '') for kw in keywords)]
    logging.info(f"      过滤效果: {original_count} -> {len(filtered_nodes)}")
    return filtered_nodes


# =================================================================================
# 主路由和聚合逻辑
# =================================================================================

@app.route('/')
def index():
    user_agent = request.headers.get('User-Agent', '').lower()
    mobile_keywords = ['mobi', 'android', 'iphone', 'ipod', 'ipad', 'windows phone', 'blackberry']
    template_name = 'mobile.html' if any(keyword in user_agent for keyword in mobile_keywords) else 'index.html'
    
    logging.info(f"检测到 {'移动端' if 'mobile' in template_name else '桌面端'} 设备，渲染 {template_name}")
    
    try:
        original_html = render_template(template_name)
    except Exception as e:
        logging.error(f"模板文件 '{template_name}' 渲染失败: {e}", exc_info=True)
        return f"<h1>Error</h1><p>Template file '{template_name}' could not be rendered. Check logs.</p>", 500
 
    # --- [修改开始] 为方便调试，添加一个“调试开关” ---
    #
    #   想要在页面上显示“查看调试日志”按钮, 请取消下面所有行的注释。
    #   正式发布时, 请确保下面所有行都被注释掉 (保持当前状态)。
    #
    # debug_link_html = '''
    # <div style="position: fixed; bottom: 10px; right: 10px; padding: 8px 12px; background-color: yellow; color: black; border: 1px solid black; border-radius: 5px; z-index: 9999; font-family: sans-serif; font-size: 14px;">
    #     <a href="/debuglog" target="_blank" style="color: black; text-decoration: none;">查看调试日志</a>
    # </div>
    # </body>
    # '''
    # if '</body>' in original_html:
    #     return original_html.replace('</body>', debug_link_html, 1)
    # else:
    #     return original_html + debug_link_html.replace('</body>', '')
    #
    # --- [修改结束] ---
 
    # 默认状态：直接返回原始HTML，不带调试按钮
    return original_html


@app.route('/aggregate/clash.yaml')
def aggregate_clash():
    logging.info("=" * 30 + " [新聚合请求开始] " + "=" * 30)
    logging.info(f"来源IP: {request.remote_addr}, User-Agent: {request.user_agent.string}")
    data = load_data()
    enabled_ids = set(data.get('aggregation_enabled', []))
    subscriptions_to_aggregate = [sub for sub in data.get('subscriptions', []) if sub.get('id') in enabled_ids]
    logging.info(
        f"- 找到 {len(data.get('subscriptions', []))} 个已存订阅，其中 {len(subscriptions_to_aggregate)} 个已启用聚合。")
    if not subscriptions_to_aggregate:
        logging.warning("  - 没有任何启用的订阅，无法生成聚合配置。")
        return make_response("没有启用的订阅，无法生成聚合配置。", 404)
    all_nodes = []
    headers = {'User-Agent': 'Clash/2023.08.17'}
    for sub_info in subscriptions_to_aggregate:
        sub_name = sub_info.get('name', 'Unnamed')
        sub_url = sub_info.get('url', '').strip()
        sub_filter_keywords = sub_info.get('filter_keywords', '')
        sub_filter_enabled = sub_info.get('filter_enabled', False)
        if not sub_url:
            logging.warning(f"  - 订阅 '{sub_name}' URL为空，已跳过。")
            continue
        logging.info(f"  - 正在处理订阅 '{sub_name}': {sub_url[:70]}...")
        try:
            sub_response = requests.get(sub_url, timeout=20, headers=headers)
            sub_response.raise_for_status()
            logging.info(f"    - 下载成功 (状态码: {sub_response.status_code})")
            raw_content = sub_response.text
            nodes_from_sub = []
            try:
                clash_config = yaml.safe_load(raw_content)
                if 'proxies' in clash_config and isinstance(clash_config['proxies'], list):
                    nodes_from_sub = clash_config['proxies']
                    logging.info(f"    - 识别为Clash配置文件格式，成功提取 {len(nodes_from_sub)} 个节点。")
            except (yaml.YAMLError, AttributeError, TypeError):
                logging.info("    - 无法解析为YAML，尝试作为链接列表处理...")
                try:
                    content = base64.b64decode(raw_content).decode('utf-8')
                    logging.info("      - Base64解码成功。")
                except (binascii.Error, UnicodeDecodeError):
                    content = raw_content
                    logging.info("      - 非Base64编码，作为纯文本处理。")
                links = content.splitlines()
                logging.info(f"      - 找到 {len(links)} 行内容，开始逐行解析...")
                for link in links:
                    if link.strip():
                        node = parse_link(link.strip())
                        if node:
                            nodes_from_sub.append(node)
                logging.info(f"    - 从链接列表成功解析出 {len(nodes_from_sub)} 个节点。")
            logging.info(f"    - 为 {len(nodes_from_sub)} 个节点添加前缀 '[{sub_name}]'")
            for node in nodes_from_sub:
                if 'name' in node and not node['name'].startswith(f"[{sub_name}] "):
                    node['name'] = f"[{sub_name}] " + node['name']
            if sub_filter_enabled and sub_filter_keywords:
                nodes_after_sub_filter = apply_filter(nodes_from_sub, sub_filter_keywords, f"订阅内[{sub_name}]")
                all_nodes.extend(nodes_after_sub_filter)
            else:
                logging.info("    - 订阅内过滤器未开启。")
                all_nodes.extend(nodes_from_sub)
        except requests.RequestException as e:
            logging.error(f"  - [错误] 下载订阅 '{sub_name}' 失败: {e}. 已跳过。")
            continue
        except Exception as e:
            logging.error(f"  - [错误] 处理订阅 '{sub_name}' 时发生未知错误: {e}", exc_info=False)
            continue
    logging.info(f"- 所有订阅处理完毕，合并后共 {len(all_nodes)} 个节点。")
    final_nodes = all_nodes
    global_filter_enabled = data.get('global_filter_enabled', False)
    global_filter_keywords = data.get('global_filter_keywords', '')
    if global_filter_enabled and global_filter_keywords:
        final_nodes = apply_filter(all_nodes, global_filter_keywords, "全局")
    else:
        logging.info("- 全局过滤器未开启。")
    logging.info(f"- 最终节点数: {len(final_nodes)} 个。")
    if not final_nodes:
        logging.warning("  - 所有节点均被过滤或获取失败，无法生成有效配置。")
        return make_response("所有节点均被过滤或获取失败，无法生成有效配置。", 400)
    try:
        template_path = CLASH_TEMPLATE_FILE
        if os.path.exists(template_path):
            logging.info(f"- 找到外部模板 '{template_path}'，将使用它。")
            with open(template_path, 'r', encoding='utf-8') as f:
                template_content = f.read()
        else:
            logging.error(f"  - [严重错误] 未找到 '{template_path}'，无法生成配置。")
            return make_response(f"错误：模板文件 '{template_path}' 未找到。", 500)
        final_config_str = generate_clash_config(final_nodes, template_content)
        response = make_response(final_config_str)
        response.headers['Content-Type'] = 'text/plain; charset=utf-8'
        response.headers['Content-Disposition'] = 'attachment; filename=clash.yaml'
        logging.info("=" * 31 + " [聚合请求成功] " + "=" * 31)
        return response
    except Exception as e:
        logging.critical(f"  - [严重错误] 生成最终配置文件时出错: {e}", exc_info=True)
        return make_response(f"服务器错误: {e}", 500)


# =================================================================================
# API 路由
# =================================================================================

@app.route('/api/data', methods=['GET'])
def get_data():
    return jsonify(load_data())


@app.route('/api/subscriptions', methods=['POST'])
def add_subscription():
    req_data = request.get_json()
    if not req_data or not req_data.get('name') or not req_data.get('url'):
        return jsonify({"message": "名称和地址不能为空"}), 400
    data = load_data()
    new_sub = {
        "id": str(int(time.time() * 1000)),
        "name": req_data['name'],
        "url": req_data['url'],
        "filter_enabled": False,
        "filter_keywords": ""
    }
    data['subscriptions'].append(new_sub)
    save_data(data)
    return jsonify({"message": "订阅添加成功"}), 201


@app.route('/api/subscriptions/<sub_id>', methods=['PUT'])
def update_subscription(sub_id):
    req_data = request.get_json()
    if not req_data:
        return jsonify({"message": "请求数据为空"}), 400
    data = load_data()
    sub_to_update = next((sub for sub in data['subscriptions'] if sub['id'] == sub_id), None)
    if sub_to_update:
        sub_to_update.update(req_data)
        save_data(data)
        is_filter_update_only = (
                                        'filter_keywords' in req_data or 'filter_enabled' in req_data) and 'name' not in req_data and 'url' not in req_data
        if is_filter_update_only:
            return jsonify({"message": "过滤器保存成功"})
        else:
            return jsonify({"message": "订阅更新成功"})
    return jsonify({"message": "未找到该订阅"}), 404


# --- [修改开始] 为删除操作添加极其详细的日志 ---
@app.route('/api/subscriptions/<sub_id>', methods=['DELETE'])
def delete_subscription(sub_id):
    logging.info("="*20 + " [DELETE REQUEST START] " + "="*20)
    logging.info(f"  - Received DELETE request for subscription ID: '{sub_id}'")
    
    data = load_data()
    
    original_sub_list = data.get('subscriptions', [])
    original_len = len(original_sub_list)
    logging.info(f"  - [Before] Number of subscriptions: {original_len}")
    
    # 查找要删除的订阅
    sub_to_delete = next((sub for sub in original_sub_list if sub.get('id') == sub_id), None)
    
    if sub_to_delete:
        logging.info(f"  - Match found! Details: Name='{sub_to_delete.get('name')}', URL='{sub_to_delete.get('url')}'")
    else:
        logging.warning(f"  - No match found for ID '{sub_id}' in the current data.")

    # 执行删除操作
    data['subscriptions'] = [sub for sub in original_sub_list if sub['id'] != sub_id]
    
    new_len = len(data['subscriptions'])
    logging.info(f"  - [After] Number of subscriptions: {new_len}")

    if new_len < original_len:
        logging.info("  - Subscription list changed. Deletion is considered successful.")
        logging.info("  - Now, cleaning the 'aggregation_enabled' list...")
        
        original_agg_list = data.get('aggregation_enabled', [])
        original_agg_len = len(original_agg_list)
        logging.info(f"    - Before cleanup, 'aggregation_enabled' has {original_agg_len} items: {original_agg_list}")

        data['aggregation_enabled'] = [id_ for id_ in original_agg_list if id_ != sub_id]
        
        new_agg_list = data.get('aggregation_enabled', [])
        new_agg_len = len(new_agg_list)
        logging.info(f"    - After cleanup, 'aggregation_enabled' has {new_agg_len} items: {new_agg_list}")
        
        save_data(data) # 持久化所有更改
        logging.info("="*20 + " [DELETE REQUEST SUCCESS] " + "="*20)
        return jsonify({"message": "订阅删除成功"})
    
    logging.error(f"  - CRITICAL: Length of subscription list did not change. Deletion failed because ID '{sub_id}' was not found.")
    logging.info("="*20 + " [DELETE REQUEST FAILED] " + "="*20)
    return jsonify({"message": "未找到该订阅"}), 404
# --- [修改结束] ---


@app.route('/api/aggregation', methods=['POST'])
def save_aggregation_settings():
    req_data = request.get_json()
    data = load_data()
    data['aggregation_enabled'] = req_data.get('enabled_ids', [])
    save_data(data)
    return jsonify({"message": "聚合选择已保存"})


@app.route('/api/filters/global', methods=['POST'])
def save_global_filter():
    req_data = request.get_json()
    data = load_data()
    data['global_filter_enabled'] = req_data.get('enabled', False)
    data['global_filter_keywords'] = req_data.get('keywords', '')
    save_data(data)
    return jsonify({"message": "全局设置已保存"})


# --- [新增开始] 日志查看路由 ---
@app.route('/debuglog')
def debug_log():
    # 增加一些方便的按钮
    clear_button = '''
    <div style="position: fixed; top: 10px; right: 10px;">
        <form method="POST" action="/debuglog/clear">
            <button type="submit" style="padding: 8px 12px; cursor: pointer;">清空日志</button>
        </form>
        <button onclick="location.reload()" style="padding: 8px 12px; cursor: pointer; margin-top: 5px;">刷新</button>
    </div>
    '''
    # 从内存中获取所有日志内容
    log_contents = log_capture_string.getvalue()
    # 使用<pre>标签来保留换行和空格，并用escape防止XSS
    return f"<html><body style='background:#111;color:#eee;font-family:monospace;line-height:1.5;padding:1em;'>{clear_button}<pre>{escape(log_contents)}</pre></body></html>"

@app.route('/debuglog/clear', methods=['POST'])
def clear_debug_log():
    # 清空内存中的日志
    log_capture_string.truncate(0)
    log_capture_string.seek(0)
    logging.info("DIAGNOSTIC: Log has been manually cleared by user.")
    # 重定向回日志页面
    return '<script>window.location.href="/debuglog";</script>'
# --- [新增结束] ---


# --- 启动逻辑 - (无改动) ---
def start_server():
    """此函数由安卓的 Chaquopy 调用，用于在后台线程中启动服务器。"""
    try:
        if not os.path.exists(CLASH_TEMPLATE_FILE):
            logging.error(f"致命错误: 模板文件 '{CLASH_TEMPLATE_FILE}' 未找到, 服务器无法启动。")
            return
        logging.info("=" * 20 + " Chaquopy Sub Aggregator 服务器启动 " + "=" * 20)
        app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
    except Exception as e:
        # 捕获启动过程中的任何异常并记录下来
        logging.critical(f"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        logging.critical(f"FATAL: Flask server failed to start: {e}")
        logging.critical(f"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", exc_info=True)

if __name__ == '__main__':
    if not IN_ANDROID:
        if not os.path.exists(CLASH_TEMPLATE_FILE):
            logging.error(f"致命错误: 模板文件 '{CLASH_TEMPLATE_FILE}' 未找到, 应用无法启动。")
            exit(1)
        logging.info("=" * 20 + " Sub Aggregator 应用启动 (Docker/Local) " + "=" * 20)
        app.run(host='0.0.0.0', port=5000, debug=False)
    else:
        logging.info("在安卓环境中被调用，等待原生代码启动服务器...")
