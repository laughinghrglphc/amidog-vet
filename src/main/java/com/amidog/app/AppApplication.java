package com.amidog.app;

import com.amidog.app.auth.UserAccount;
import com.amidog.app.auth.UserAccountRepository;
import com.amidog.app.auth.EmailVerificationToken;
import com.amidog.app.auth.EmailVerificationTokenRepository;
import com.amidog.app.auth.PasswordResetToken;
import com.amidog.app.auth.PasswordResetTokenRepository;
import com.amidog.app.auth.UserExternalIdentity;
import com.amidog.app.auth.UserExternalIdentityRepository;
import com.amidog.app.auth.EmailDeliveryJob;
import com.amidog.app.auth.EmailDeliveryJobRepository;
import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.client.Pet;
import com.amidog.app.client.PetRepository;
import com.amidog.app.catalog.ServiceOffering;
import com.amidog.app.catalog.ServiceOfferingRepository;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.notification.Notification;
import com.amidog.app.notification.NotificationRepository;
import com.amidog.app.notification.NotificationReminderProperties;
import com.amidog.app.reservation.Reservation;
import com.amidog.app.reservation.ReservationRepository;
import com.amidog.app.scheduling.WeeklyAvailability;
import com.amidog.app.scheduling.WeeklyAvailabilityRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan(basePackageClasses = {
        UserAccount.class,
        UserExternalIdentity.class,
        EmailVerificationToken.class,
        PasswordResetToken.class,
        EmailDeliveryJob.class,
        Client.class,
        Pet.class,
        ServiceOffering.class,
        WeeklyAvailability.class,
        Reservation.class,
        Notification.class
})
@EnableJpaRepositories(basePackageClasses = {
        UserAccountRepository.class,
        UserExternalIdentityRepository.class,
        EmailVerificationTokenRepository.class,
        PasswordResetTokenRepository.class,
        EmailDeliveryJobRepository.class,
        ClientRepository.class,
        PetRepository.class,
        ServiceOfferingRepository.class,
        WeeklyAvailabilityRepository.class,
        ReservationRepository.class,
        NotificationRepository.class
})
@EnableConfigurationProperties({
        AmidogProperties.class,
        NotificationReminderProperties.class
})
@EnableScheduling
public class AppApplication {

	public static void main(String[] args) {
		SpringApplication.run(AppApplication.class, args);
	}

}
