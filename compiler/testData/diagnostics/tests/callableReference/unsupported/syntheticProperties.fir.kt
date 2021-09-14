// FILE: Customer.java
public class Customer {
    private String name;

    public Customer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

// FILE: test.kt
val customerName = Customer::<!UNSUPPORTED!>name<!>

fun <K> id(x: K) = x

fun main() {
    val customerName = id(Customer::name)
}