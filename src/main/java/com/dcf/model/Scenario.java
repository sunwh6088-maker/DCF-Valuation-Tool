package com.dcf.model;

/**
 * 估值情景（保守 / 中性 / 乐观）。
 *
 * <p>三情景在用户设定的基础参数上自动施加偏移，用于展示估值区间而非单点：
 * <ul>
 *   <li>保守：增长率 -2pp、折现率 +1.5pp、永续增长率 -0.5pp（估值最低）</li>
 *   <li>中性：与用户参数完全一致（BASE）</li>
 *   <li>乐观：增长率 +2pp、折现率 -1.5pp、永续增长率 +0.5pp（估值最高）</li>
 * </ul>
 * 偏移量均为可讨论的业务约定，后续可改为参数页可配置。
 */
public enum Scenario {

    CONSERVATIVE("保守", -0.02, +0.015, -0.005),
    BASE("中性", 0.0, 0.0, 0.0),
    OPTIMISTIC("乐观", +0.02, -0.015, +0.005);

    private final String label;
    private final double gFirstDelta;
    private final double discountDelta;
    private final double gTerminalDelta;

    Scenario(String label, double gFirstDelta, double discountDelta, double gTerminalDelta) {
        this.label = label;
        this.gFirstDelta = gFirstDelta;
        this.discountDelta = discountDelta;
        this.gTerminalDelta = gTerminalDelta;
    }

    /** 中文标签（保守/中性/乐观）。 */
    public String label() {
        return label;
    }

    /** 高增长期增长率偏移（相对用户输入）。 */
    public double gFirstDelta() {
        return gFirstDelta;
    }

    /** 折现率偏移（相对最终折现率，含 WACC/CAPM/手动）。 */
    public double discountDelta() {
        return discountDelta;
    }

    /** 永续增长率偏移（相对用户输入）。 */
    public double gTerminalDelta() {
        return gTerminalDelta;
    }
}
