package jonmcewen.camel.example.metrics;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.metrics.routepolicy.MetricsRoutePolicyFactory;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteSender;
import com.codahale.metrics.graphite.GraphiteUDP;

/**
 * @author jon.mcewen
 * 
 *         A simple Spring Boot application, with a couple of timed camel routes
 *         configured with camel-metrics. Reports metrics to Graphite via
 *         dropwizard-metrics GraphiteUDP sender. Has standard spring-actuator
 *         endpoints such as /beans, /autoconfig, /metrics
 *
 */
@SpringBootApplication
public class Application {

	private static final Logger log = LoggerFactory.getLogger(Application.class);

	@Autowired
	private MetricRegistry metricRegistry;

	/**
	 * @param args
	 *            no command line args required
	 */
	public static void main(String[] args) {
		log.info(" *** STARTING CAMEL METRICS EXAMPLE APPLICATION ***");
		SpringApplication.run(Application.class, args);

	}

	/**
	 * Create reporter bean and tell Spring to call stop() when shutting down.
	 * UPD must be enabled in carbon.conf
	 * 
	 * @return graphite reporter
	 */
	@Bean(destroyMethod = "stop")
	public GraphiteReporter graphiteReporter() {
		final GraphiteSender graphite = new GraphiteUDP(new InetSocketAddress("localhost", 2003));
		final GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry).prefixedWith("camel-spring-boot").convertRatesTo(TimeUnit.SECONDS)
				.convertDurationsTo(TimeUnit.MILLISECONDS).filter(MetricFilter.ALL).build(graphite);
		reporter.start(1, TimeUnit.MINUTES);
		return reporter;
	}

	/**
	 * @return timed route that logs output every 6 seconds
	 */
	@Bean
	public RouteBuilder slowRoute() {
		return new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from("timer://foo?period=6000&daemon=false").routeId("slow-route").setBody().constant("Slow hello world!").log("${body}");
			}
		};
	}

	/**
	 * @return timed route that logs output every 2 seconds
	 */
	@Bean
	public RouteBuilder fastRoute() {
		return new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from("timer://foo?period=2000&daemon=false").routeId("fast-route").setBody().constant("Fast hello world!").log("${body}");
			}
		};
	}

	@Bean
	CamelContextConfiguration contextConfiguration() {
		return new CamelContextConfiguration() {

			@Override
			public void beforeApplicationStart(CamelContext context) {
				log.info("Configuring camel metrics on all routes");
				MetricsRoutePolicyFactory fac = new MetricsRoutePolicyFactory();
				fac.setMetricsRegistry(metricRegistry);
				context.addRoutePolicyFactory(fac);
			}
		};
	}

}
