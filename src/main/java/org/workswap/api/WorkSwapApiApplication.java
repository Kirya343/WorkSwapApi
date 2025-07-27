package org.workswap.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.workswap")
@EnableScheduling
@EnableTransactionManagement
public class WorkSwapApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorkSwapApiApplication.class, args);
	}

}
