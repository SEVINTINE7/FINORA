package lk.finora.app.data.api;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class CategoryApi {
    private final HttpUrl collection;

    public CategoryApi(HttpUrl origin) {
        collection = origin.newBuilder().addPathSegments("api/v1/categories").build();
    }

    public Request list() {
        return new Request.Builder().url(collection).build();
    }

    public Request get(long id) {
        return item(id).build();
    }

    public Request create(RequestBody body) {
        return new Request.Builder().url(collection).post(body).build();
    }

    public Request update(long id, RequestBody body) {
        return item(id).put(body).build();
    }

    public Request delete(long id) {
        return item(id).delete().build();
    }

    private Request.Builder item(long id) {
        return new Request.Builder().url(collection.newBuilder().addPathSegment(Long.toString(id)).build());
    }
}
