
import io.micrometer.core.instrument.MeterRegistry;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

@Path("/example")
@Produces("text/plain")
public class ExampleResource {

    private final MeterRegistry registry;

    ExampleResource(MeterRegistry registry) {
        this.registry = registry;
    }

    @GET
    @Path("prime/{number}")
    public String checkIfPrime(@PathParam("number") long number) {
        if (number < 1) {
            return "Only natural numbers can be prime numbers.";
        }
        if (number == 1 || number == 2 || number % 2 == 0) {
            return number + " is not prime.";
        }

        if ( testPrimeNumber(number) ) {
            return number + " is prime.";
        } else {
            return number + " is not prime.";
        }
    }

    protected boolean testPrimeNumber(long number) {
        // Count the number of times we test for a prime number
        registry.counter("example.prime.number").increment();
        for (int i = 3; i < Math.floor(Math.sqrt(number)) + 1; i = i + 2) {
            if (number % i == 0) {
                return false;
            }
        }
        return true;
    }
}
