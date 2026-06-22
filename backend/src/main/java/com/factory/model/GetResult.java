package com.factory.model;

public class GetResult {

    private final FactoryResult result;
    private final int value;

    private GetResult(FactoryResult result, int value) {
        this.result = result;
        this.value = value;
    }

    public static GetResult success(int value) {
        return new GetResult(FactoryResult.SUCCESS, value);
    }

    public static GetResult interrupted() {
        return new GetResult(FactoryResult.INTERRUPTED, -1);
    }

    public static GetResult invalid() {
        return new GetResult(FactoryResult.INVALID, -1);
    }

    public FactoryResult getResult() {
        return result;
    }

    public int getValue() {
        return value;
    }

    public boolean isSuccess() {
        return result == FactoryResult.SUCCESS;
    }

    public boolean isInterrupted() {
        return result == FactoryResult.INTERRUPTED;
    }

    public boolean isInvalid() {
        return result == FactoryResult.INVALID;
    }

    @Override
    public String toString() {
        if (result == FactoryResult.SUCCESS) {
            return "GetResult{result=" + result + ", value=" + value + "}";
        }
        return "GetResult{result=" + result + "}";
    }
}
