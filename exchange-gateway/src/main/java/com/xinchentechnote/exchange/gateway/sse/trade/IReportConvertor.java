package com.xinchentechnote.exchange.gateway.sse.trade;

import com.xinchentechnote.exchange.gateway.sse.CommandWrapper;

public interface IReportConvertor<S, T> {

    T convert(S trade, CommandWrapper requestWrapper);
}
