package com.xinchentechnote.exchange.simulator.sse.loaddata;

import java.util.List;

public interface IDataLoadService<T> {

    List<T> loadData(String path);

}
