package com.mcverse.jobify.application.service;

import com.mcverse.jobify.application.model.Application;
import com.mcverse.jobify.application.model.ApplicationStatus;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.notification.model.NotificationType;
import com.mcverse.jobify.notification.service.NotificationService;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.model.Seeker;
import org.springframework.stereotype.Component;

/** Turns application events into notifications: employers hear about new and withdrawn applications, seekers about stages. */
@Component
public class ApplicationNotifier {

    private final NotificationService notifications;

    public ApplicationNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** A seeker applied (or applied again): tell the employer who owns the job. */
    public void applicationSubmitted(Application application) {
        JobPost job = application.getJob();
        Employer employer = job.getEmployer();
        if (employer == null) {
            return;
        }
        notifications.notify(employer.getUsername(), NotificationType.APPLICATION, "New application",
                applicantName(application) + " applied for " + job.getJobTitle() + ".",
                job.getPostId(), application.getId());
    }

    /** The status changed: a withdrawal goes to the employer, every other change to the seeker. */
    public void statusChanged(Application application, ApplicationStatus newStatus) {
        JobPost job = application.getJob();
        if (newStatus == ApplicationStatus.WITHDRAWN) {
            Employer employer = job.getEmployer();
            if (employer != null) {
                notifications.notify(employer.getUsername(), NotificationType.APPLICATION, "Application withdrawn",
                        applicantName(application) + " withdrew their application for " + job.getJobTitle() + ".",
                        job.getPostId(), application.getId());
            }
            return;
        }
        String who = companyOrEmployer(job);
        String title = job.getJobTitle();
        String seeker = application.getSeeker().getUsername();
        switch (newStatus) {
            case IN_REVIEW -> notifications.notify(seeker, NotificationType.APPLICATION,
                    "Your application is being reviewed",
                    who + " is now reviewing your application for " + title + ".",
                    job.getPostId(), application.getId());
            case INTERVIEW -> notifications.notify(seeker, NotificationType.INTERVIEW,
                    "You have reached the interview stage",
                    "Good news: your application for " + title + " at " + who + " moved to the interview stage.",
                    job.getPostId(), application.getId());
            case OFFER -> notifications.notify(seeker, NotificationType.HIRING, "You have an offer",
                    who + " has made you an offer for " + title + ".",
                    job.getPostId(), application.getId());
            case REJECTED -> notifications.notify(seeker, NotificationType.APPLICATION, "Application update",
                    "Your application for " + title + " at " + who + " was not taken forward.",
                    job.getPostId(), application.getId());
            default -> { /* APPLIED is only ever the starting state: nothing to announce */ }
        }
    }

    private static String applicantName(Application application) {
        Seeker seeker = application.getSeeker();
        return seeker.getName() + " " + seeker.getLastName();
    }

    private static String companyOrEmployer(JobPost job) {
        Employer employer = job.getEmployer();
        if (employer == null) {
            return "The employer";
        }
        return employer.getCompany() != null ? employer.getCompany().getName() : employer.getUsername();
    }
}
