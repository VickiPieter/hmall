package com.hmall.payservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmall.payservice.domain.dto.PayApplyDTO;
import com.hmall.payservice.domain.dto.PayOrderFormDTO;
import com.hmall.payservice.domain.po.PayOrder;

/**
 * <p>
 * 支付订单 服务类
 * </p>
 */
public interface IPayOrderService extends IService<PayOrder> {

    String applyPayOrder(PayApplyDTO applyDTO);

    void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO);
}
