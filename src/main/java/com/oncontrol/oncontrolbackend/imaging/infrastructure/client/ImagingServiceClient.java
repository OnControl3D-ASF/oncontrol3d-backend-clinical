package com.oncontrol.oncontrolbackend.imaging.infrastructure.client;

import com.oncontrol.oncontrolbackend.imaging.application.dto.LungSegmentationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class ImagingServiceClient {

    private final RestClient restClient;

    /**
     * Explicit timeouts: without them the detected HTTP library default applies (Reactor Netty: 10 s read),
     * shorter than a segmentation on AKS (~30 s: study download from Orthanc + ITK), so slow runs became 500s.
     */
    public ImagingServiceClient(
            @Value("${app.imaging-service.url}") String imagingServiceUrl,
            @Value("${app.imaging-service.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.imaging-service.read-timeout:180s}") Duration readTimeout
    ) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(imagingServiceUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    public LungSegmentationResponse segmentLungs(
            String orthancStudyId,
            Integer thresholdHu,
            Integer borderErosionRadius,
            Boolean force
    ) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/studies/{orthancStudyId}/segment-lungs")
                        .queryParam("threshold_hu", thresholdHu)
                        .queryParam("border_erosion_radius", borderErosionRadius)
                        .queryParam("force", force)
                        .build(orthancStudyId))
                .retrieve()
                .body(LungSegmentationResponse.class);
    }

    public LungSegmentationResponse getLungViewerUrl(String orthancStudyId) {
        return restClient.get()
                .uri("/studies/{orthancStudyId}/viewer-url", orthancStudyId)
                .retrieve()
                .body(LungSegmentationResponse.class);
    }
}