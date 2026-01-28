package com.aseubel.yusi.pojo.dto.geo;

import java.io.Serializable;

public class ReverseGeocodeResult implements Serializable {
    private String address;
    private String district;
    private String city;

    public ReverseGeocodeResult() {
    }

    public ReverseGeocodeResult(String address, String district, String city) {
        this.address = address;
        this.district = district;
        this.city = city;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
