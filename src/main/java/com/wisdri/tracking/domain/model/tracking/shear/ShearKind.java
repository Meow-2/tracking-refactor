package com.wisdri.tracking.domain.model.tracking.shear;

/**
 * 剪切逻辑类型。
 */
public enum ShearKind {
    /** 切除物料带头。 */
    HEAD,
    /** 同一物料中间分切。 */
    SLICE,
    /** 切除物料带尾。 */
    TAIL
}
