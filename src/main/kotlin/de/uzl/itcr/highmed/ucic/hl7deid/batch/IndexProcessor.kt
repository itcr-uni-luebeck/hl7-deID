package de.uzl.itcr.highmed.ucic.hl7deid.batch

import de.uzl.itcr.highmed.ucic.hl7analysis.messageindex.Hl7MessageIndexer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.tasklet.MethodInvokingTaskletAdapter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableBatchProcessing
class IndexProcessor(
    @Autowired val jobBuilderFactory: JobBuilderFactory,
    @Autowired val stepBuilderFactory: StepBuilderFactory,
    @Autowired val hl7MessageIndexer: Hl7MessageIndexer
) {

    @Bean
    fun indexStep() = stepBuilderFactory.get("indexStep")
        .tasklet(indexFileTasklet())
        .build()

    @Bean
    @StepScope
    fun indexFileTasklet(
        @Value("#{jobParameters['filename']}") filename: String? = null
    ) = MethodInvokingTaskletAdapter().apply {
        setTargetObject(hl7MessageIndexer)
        setTargetMethod("indexHl7MessageFile")
        setArguments(arrayOf(filename))
    }

    @Bean
    fun indexFileJob() = jobBuilderFactory.get("indexFileJob")
        .incrementer(RunIdIncrementer())
        .listener(IndexCompletionListener())
        .flow(indexStep())
        .end()
        .build()

    class IndexCompletionListener : JobExecutionListenerSupport() {
        private val log: Logger = LoggerFactory.getLogger(IndexProcessor::class.java)
        override fun afterJob(jobExecution: JobExecution) {
            log.info("Processed index job: $jobExecution")
        }

    }

}