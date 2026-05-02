package com.xinchentechnote.exchange.simulator.loaddata;

import java.util.List;

public interface IDataLoadService<T> {

    List<T> loadData(String path);

}
