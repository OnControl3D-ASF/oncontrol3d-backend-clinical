package com.oncontrol.oncontrolbackend.dashboard.application.service;

import com.oncontrol.oncontrolbackend.appointments.domain.repository.AppointmentRepository;
import com.oncontrol.oncontrolbackend.dashboard.application.dto.DoctorReportsResponse;
import com.oncontrol.oncontrolbackend.medicalrecords.domain.model.TreatmentStatus;
import com.oncontrol.oncontrolbackend.profiles.domain.repository.PatientProfileRepository;
import com.oncontrol.oncontrolbackend.support.Fixtures;
import com.oncontrol.oncontrolbackend.treatments.domain.model.TreatmentType;
import com.oncontrol.oncontrolbackend.treatments.domain.repository.TreatmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Covers the values ReportService actually computes from data (totals, type
 * distribution, month buckets). Several other figures in the response are
 * hardcoded placeholders and are intentionally NOT asserted here.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private TreatmentRepository treatmentRepository;
    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private ReportService service;

    @BeforeEach
    void setUp() {
        when(patientProfileRepository.findByDoctorProfileId(Fixtures.DOCTOR_ID))
                .thenReturn(List.of(Fixtures.patient(), Fixtures.patient(), Fixtures.patient()));
        when(appointmentRepository.findByDoctorId(Fixtures.DOCTOR_ID)).thenReturn(Collections.nCopies(14, null));
        when(treatmentRepository.countByDoctorIdAndStatus(Fixtures.DOCTOR_ID, TreatmentStatus.ACTIVE)).thenReturn(4L);
        when(treatmentRepository.countByDoctorIdAndStatus(Fixtures.DOCTOR_ID, TreatmentStatus.COMPLETED)).thenReturn(2L);
        when(treatmentRepository.countByDoctorIdAndStatus(Fixtures.DOCTOR_ID, TreatmentStatus.SUSPENDED)).thenReturn(1L);
        when(treatmentRepository.countByDoctorIdAndType(eq(Fixtures.DOCTOR_ID), any())).thenReturn(0L);
        when(treatmentRepository.countByDoctorIdAndType(Fixtures.DOCTOR_ID, TreatmentType.CHEMOTHERAPY)).thenReturn(3L);
        when(treatmentRepository.countByDoctorIdAndType(Fixtures.DOCTOR_ID, TreatmentType.RADIOTHERAPY)).thenReturn(1L);
    }

    @Test
    void countsPatientsAndTreatmentsFromRepositories() {
        DoctorReportsResponse report = service.getDoctorReports(Fixtures.DOCTOR_ID, null);

        assertThat(report.getPatients().getTotal()).isEqualTo(3L);
        assertThat(report.getTreatments().getActive()).isEqualTo(4L);
        assertThat(report.getTreatments().getCompleted()).isEqualTo(2L);
        assertThat(report.getTreatments().getSuspended()).isEqualTo(1L);
    }

    @Test
    void treatmentDistributionUsesIntegerPercentages() {
        DoctorReportsResponse report = service.getDoctorReports(Fixtures.DOCTOR_ID, null);

        assertThat(report.getTreatmentsByType())
                .extracting(DoctorReportsResponse.TypeDistribution::getType,
                        DoctorReportsResponse.TypeDistribution::getCount,
                        DoctorReportsResponse.TypeDistribution::getPercentage)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("CHEMOTHERAPY", 3L, 75),
                        org.assertj.core.groups.Tuple.tuple("RADIOTHERAPY", 1L, 25));
    }

    @Test
    void monthlySeriesDefaultsToSixMonthsEndingCurrentMonth() {
        DoctorReportsResponse report = service.getDoctorReports(Fixtures.DOCTOR_ID, null);

        assertThat(report.getPatientsByMonth()).hasSize(6);
        assertThat(report.getPatientsByMonth().get(5).getMonth()).isEqualTo(YearMonth.now().toString());
    }

    @Test
    void monthlySeriesHonoursRequestedMonths() {
        assertThat(service.getDoctorReports(Fixtures.DOCTOR_ID, 3).getPatientsByMonth()).hasSize(3);
    }

    @Test
    void appointmentsByDayCoversTheWholeWeek() {
        DoctorReportsResponse report = service.getDoctorReports(Fixtures.DOCTOR_ID, null);

        assertThat(report.getAppointmentsByDay())
                .extracting(DoctorReportsResponse.DayDistribution::getDay)
                .containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    }
}
