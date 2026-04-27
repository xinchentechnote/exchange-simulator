package com.xinchentechnote.exchange.simulator.sse.trade;

import com.xinchentechnote.exchange.simulator.sse.CommandWrapper;

public interface IReportConvertor<S, T> {

    T convert(S trade, CommandWrapper requestWrapper);
}
