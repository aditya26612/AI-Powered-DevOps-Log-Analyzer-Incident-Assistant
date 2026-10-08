package com.project.log_layer.util;

/**
 * Standard metadata keys shared across parser implementations.
 */
public final class ParserMetadataKeys {

    private ParserMetadataKeys() {
        throw new IllegalStateException("Utility class");
    }

    /*
     * Spring Boot
     */
    public static final String PID = "pid";
    public static final String PACKAGE_NAME = "packageName";
    public static final String CLASS_NAME = "className";

    /*
     * Docker
     */
    public static final String CONTAINER_ID = "containerId";
    public static final String CONTAINER_NAME = "containerName";

    /*
     * Docker / Kubernetes
     */

    public static final String FLAG = "flag";



    /*
     * Kubernetes
     */
    public static final String POD_NAME = "podName";
    public static final String NAMESPACE = "namespace";
    public static final String STREAM = "stream";

    /*
     * Nginx
     */
    public static final String CLIENT_IP = "clientIp";
    public static final String HTTP_METHOD = "httpMethod";
    public static final String REQUEST_URL = "requestUrl";
    public static final String PROTOCOL = "protocol";
    public static final String STATUS_CODE = "statusCode";
    public static final String RESPONSE_SIZE = "responseSize";
    public static final String REFERRER = "referrer";
    public static final String USER_AGENT = "userAgent";

}