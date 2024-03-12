package kamon.instrumentation.apache.httpclient;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;

import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage.Scope;
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler;

public class ResponseHandlerProxy<T, U> implements ResponseHandler<T> {

    private final ResponseHandler<T> delegate;
    private final RequestHandler<U> handler;
    private Context parentContext;

    public ResponseHandlerProxy(RequestHandler<U> handler, ResponseHandler<T> delegate, Context parentContext) {
        this.handler = handler;
        this.delegate = delegate;
        this.parentContext = parentContext;
    }

    @Override
    public T handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
        ApacheHttpClientInstrumentation.processResponse(handler, response, null);
        // run original handler in parent context to avoid nesting of spans
        try (Scope ignored = Kamon.storeContext(parentContext)) {
            return delegate.handleResponse(response);
        }
    }

}