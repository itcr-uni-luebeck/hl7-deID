package de.uzl.itcr.highmed.ucic.hl7deid.batch

import de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.services.Hl7PseudoymizationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.DefaultJobParametersValidator
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.tasklet.MethodInvokingTaskletAdapter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor

@Configuration
class PseudoProcessor(
    @Autowired val jobBuilderFactory: JobBuilderFactory,
    @Autowired val stepBuilderFactory: StepBuilderFactory,
    @Autowired val pseudoymizationService: Hl7PseudoymizationService
) {

    @Bean
    fun paramsValidator() = DefaultJobParametersValidator(
        arrayOf(INPUTPARAM, OUTPUTPARAM), arrayOf()
    )

    @Bean
    fun pseudonymizeJob() =
        jobBuilderFactory.get("pseudoJob")
            //.preventRestart()
            //.validator(paramsValidator())
            .incrementer(RunIdIncrementer())
            .listener(JobCompletionNotificationListener())
            .flow(pseudoStep())
            .next(moveStep())
            .end()
            .build()

    @Bean
    fun pseudoStep() = stepBuilderFactory.get("pseudoStep")
        .tasklet(pseudoTasklet())
        .build()

    @Bean
    fun moveStep() = stepBuilderFactory.get("moveStep")
        .tasklet(moveFileTasklet())
        .build()

    @Bean
    @StepScope
    fun moveFileTasklet(
        @Value("#{jobParameters['input.file']}") inputFileName: String? = null,
        @Value("#{jobParameters['moveto.directory']}") moveToDirectory: String? = null
    ) = MethodInvokingTaskletAdapter().apply {
        setTargetObject(pseudoymizationService)
        setTargetMethod("moveFile")
        setArguments(arrayOf(inputFileName, moveToDirectory))
    }

    @Bean
    @StepScope
    fun pseudoTasklet(
        @Value("#{jobParameters['input.file']}") inputFileName: String? = null,
        @Value("#{jobParameters['output.directory']}") outputFileName: String? = null,
        @Value("#{jobParameters['changeFilenameToMsgId']}") changeFilenameToMsgId: String? = null,
        @Value("#{jobParameters['includeTriggerEvent']}") includeTriggerEvent: String? = null,
        ) = MethodInvokingTaskletAdapter().apply {
        setTargetObject(pseudoymizationService)
        setTargetMethod("processFromFileToFile")
        setArguments(arrayOf(inputFileName, outputFileName, changeFilenameToMsgId.toBoolean(), includeTriggerEvent.toBoolean()))
    }

    companion object {
        const val INPUTPARAM = "input.file"
        const val OUTPUTPARAM = "output.directory"
        const val MOVETOPARAM = "moveto.directory"
        const val CHANGEFILENAMEPARAM = "changeFilenameToMsgId"
        const val INCLUDETRIGGERPARAM = "includeTriggerEvent"
    }
}

class JobCompletionNotificationListener : JobExecutionListenerSupport() {

    private val log: Logger = LoggerFactory.getLogger(JobCompletionNotificationListener::class.java)

    override fun afterJob(jobExecution: JobExecution) {
        log.info("Processed pseudo job: $jobExecution")
    }
}