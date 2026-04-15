package com.xinchentechnote.exchange.gateway.sse.loaddata;

import java.util.List;

public interface IDataLoadService<T> {

    List<T> loadData(String path);

}
