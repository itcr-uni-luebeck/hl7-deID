package de.uzl.itcr.highmed.ucic.hl7analysis

import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.HapiContext
import ca.uhn.hl7v2.model.v25.message.ADT_AXX
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory
import ca.uhn.hl7v2.parser.ParserConfiguration
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo.PatientPseudonymRepo
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.model.NameList
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.model.PseudonymisationRulesSettings
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.model.PseudonymizationRules
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo.PatientIdentityRepo
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.services.PatientIdentityService
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.task.SimpleAsyncTaskExecutor
import java.security.SecureRandom

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableBatchProcessing
class Hl7AnalysisApplication(
    @Autowired val pseudonymisationRulesSettings: PseudonymisationRulesSettings,
    @Autowired val jobRepository: JobRepository
) {
    @Bean
    fun hapiContext(): HapiContext = DefaultHapiContext().apply {
        validationContext = ValidationContextFactory.noValidation()
        parserConfiguration = parserConfiguration?.apply {
            isValidating = false
        }
        //modelClassFactory = CanonicalModelClassFactory(ADT_AXX::class.java)
    }

    @Bean
    fun asyncJobLauncher() = SimpleJobLauncher().apply {
        setJobRepository(jobRepository)
        setTaskExecutor(SimpleAsyncTaskExecutor().apply {
            concurrencyLimit = 1
        })
        afterPropertiesSet()
    }

    @Bean
    fun pseudonymizationRules() = PseudonymizationRules.readRules(pseudonymisationRulesSettings)

    @Bean
    fun nameList() = NameList.readNameList()

    @Bean
    fun identityService(
        patientIdentityRepo: PatientIdentityRepo,
        patientPseudonymRepo: PatientPseudonymRepo,
        nameList: NameList,
        random: SecureRandom
    ) = PatientIdentityService(patientIdentityRepo, patientPseudonymRepo, nameList, random)

    @Bean
    fun random() = SecureRandom()
}

fun main(args: Array<String>) {
    runApplication<Hl7AnalysisApplication>(*args)
}
