package de.otto.synapse.example.producer;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Payload;

public class Product implements Payload{

    @JsonProperty
    private String id;
    @JsonProperty
    private long price;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }



    @Override
    public String toString() {
        return "ProductPayload{" +
                "id='" + id + '\'' +
                ", price=" + price +
                '}';
    }
}
