package android.os;

public class ServiceSpecificException extends RuntimeException {
    public ServiceSpecificException(int errorCode, String message) {
        super(message);
        throw new RuntimeException();
    }
}
