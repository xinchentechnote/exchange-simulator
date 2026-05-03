package com.xinchentechnote.exchange.simulator.convertor;

import exchange.core2.core.IEventsHandler;

public interface CommandResultConvertor<O,T> {
    T convert(O origin, IEventsHandler.ApiCommandResult commandResult);
}
