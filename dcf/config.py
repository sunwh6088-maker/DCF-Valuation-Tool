"""全局默认参数与常量。

所有数值均可在 UI 中手动覆盖，此处仅提供默认值。
"""

# 默认市场参数（使用时点最新值，报告会标注数据日期）
DEFAULT_RF_CN = 0.017          # 中国 10 年期国债收益率（约 1.7%，可覆盖）
DEFAULT_RF_US = 0.043          # 美国 10 年期国债收益率（约 4.3%，可覆盖）
DEFAULT_ERP_CN = 0.055         # 中国股权风险溢价（Damodaran 口径约 5%-6%）
DEFAULT_ERP_US = 0.045         # 美国股权风险溢价（Damodaran 口径约 4%-5%）
DEFAULT_TERMINAL_GROWTH = 0.025  # 永续增长率默认 2.5%（2%-3% 区间）
DEFAULT_TAX_RATE = 0.25        # 默认有效税率（建议用历史均值覆盖）

# 模型结构
HISTORY_YEARS = 10             # 历史数据年数（5-10 年）
FORECAST_YEARS = 10            # 显式预测期年数
FIRST_STAGE_YEARS = 5          # 高增长期年数（前 5 年固定增长率）
TRANSITION_YEARS = 5           # 过渡期年数（线性过渡到永续增长率）

# 输入范围校验
RANGE_GROWTH = (-0.50, 0.50)   # 增长率 -50% ~ 50%
RANGE_DISCOUNT = (0.001, 0.30) # 折现率 0.1% ~ 30%
RANGE_TERMINAL = (0.0, 0.05)   # 永续增长率 0% ~ 5%
RANGE_BETA = (0.0, 3.0)        # Beta 0 ~ 3
RANGE_ERP = (0.0, 0.15)        # 市场风险溢价 0% ~ 15%
RANGE_TAX = (0.0, 0.50)        # 税率 0% ~ 50%
RANGE_RF = (0.0, 0.10)         # 无风险利率 0% ~ 10%
RANGE_SHARES = (0.0, 1e13)     # 总股本 > 0
RANGE_PRICE = (0.0, 1e9)       # 股价 > 0

# 缓存与输出
CACHE_DIR = "data/cache"
OUTPUT_DIR = "outputs"