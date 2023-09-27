package com.blabla.executor;

public enum Status {
    READY, // 就绪
    RUNNING, // 运行中
    COMPLETED, // 正常结束
    TIMEOUT, // 超时
    INTERRUPTED; // 被终止，shutDown

    /**
     * @return 是否已经完成
     */
    public boolean hasClosed() {
        return this == COMPLETED || this == INTERRUPTED || this == TIMEOUT;
    }

}
