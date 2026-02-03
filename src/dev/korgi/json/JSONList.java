package dev.korgi.json;

import java.util.List;

public class JSONList<T> {

    @SuppressWarnings("unused")
    private List<T> object;

    public JSONList(List<T> object) {
        this.object = object;
    }
}
