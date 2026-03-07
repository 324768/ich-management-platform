package com.hyang.ich.common.constant;

public class CommonConstant {

    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页数量 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 最大每页数量 */
    public static final int MAX_PAGE_SIZE = 100;

    /** Token请求头 */
    public static final String TOKEN_HEADER = "Authorization";

    /** Token前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 未删除 */
    public static final int NOT_DELETED = 0;

    /** 已删除 */
    public static final int DELETED = 1;

    /** 状态：启用 */
    public static final int STATUS_ENABLE = 1;

    /** 状态：禁用 */
    public static final int STATUS_DISABLE = 0;

    private CommonConstant() {}
}
