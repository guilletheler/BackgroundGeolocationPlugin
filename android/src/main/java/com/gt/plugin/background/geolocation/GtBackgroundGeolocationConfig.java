package com.gt.plugin.background.geolocation;

public class GtBackgroundGeolocationConfig {
    private String url;
    private String bearerToken;
    private long interval;
    private String notificationTitle;
    private String notificationText;
    private String icon;
    private String messageTemplate;
    private long maxInterval = 15 * 60 * 1000;
    private Integer minDist = 50;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public String getNotificationTitle() {
        return notificationTitle;
    }

    public void setNotificationTitle(String notificationTitle) {
        this.notificationTitle = notificationTitle;
    }

    public String getNotificationText() {
        return notificationText;
    }

    public void setNotificationText(String notificationText) {
        this.notificationText = notificationText;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    public void setMessageTemplate(String messageTemplate) {
        this.messageTemplate = messageTemplate;
    }

    public long getMaxInterval() {
        return maxInterval;
    }

    public void setMaxInterval(long minInterval) {
        this.maxInterval = minInterval;
    }

    public Integer getMinDist() {
        return minDist;
    }

    public void setMinDist(Integer minDist) {
        this.minDist = minDist;
    }
}
