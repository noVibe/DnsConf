package com.novibe.dns.next_dns.http;

import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.dto.response.NextDnsResponse;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

@UtilityClass
public class NextDnsApiProcessor {

    public <D, R extends NextDnsResponse<?>> void callApi(List<D> requestList, Function<D, R> request) {
        for (int i = 0; i < requestList.size(); i++) {
            R response = request.apply(requestList.get(i));
            if (ofNullable(response).map(NextDnsResponse::getErrors).isPresent()) {
                Log.fail("Failed request: " + response.getErrors());
            } else {
                Log.progress("Current success progress: " + (i + 1) + "/" + requestList.size());
            }
        }
        Log.common("\nCompleted");
    }

}
