package de.uzl.itcr.highmed.ucic.hl7deid.batch

import de.uzl.itcr.highmed.ucic.hl7analysis.batch.DirWatcher
import de.uzl.itcr.highmed.ucic.hl7analysis.batch.PseudoProcessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Path
import javax.annotation.PostConstruct

@Service
@EnableScheduling
@Profile("pseudofilewatcher")
class PseudoFileDirectoryWatcher(
    @Autowired val pseudonymizeJob: Job,
    @Autowired val watcherProperties: DirectoryWatcherProperties,
    @Autowired asyncJobLauncher: JobLauncher,
) : DirWatcher(watcherProperties.inputPath, asyncJobLauncher) {

    override val log: Logger = LoggerFactory.getLogger(PseudoFileDirectoryWatcher::class.java)

    @PostConstruct
    fun postConstruct() {
        log.info("Started watching directory: ${watcherProperties.inputPath}.")
        log.info("Will move completed files to ${watcherProperties.moveToPath}")
        log.info("Outputting files to ${watcherProperties.outputPath}")
    }

    override fun configureJob(newFile: File): Pair<Job, JobParameters> = pseudonymizeJob to JobParametersBuilder()
        .addString(PseudoProcessor.INPUTPARAM, newFile.absolutePath)
        .addString(PseudoProcessor.OUTPUTPARAM, watcherProperties.outputPath.absolutePath)
        .addString(PseudoProcessor.MOVETOPARAM, watcherProperties.moveToPath.absolutePath)
        .addString(
            PseudoProcessor.CHANGEFILENAMEPARAM,
            watcherProperties.changeFilenameToMsgId.toString()
        )
        .toJobParameters()

}

@ConstructorBinding
@ConfigurationProperties(prefix = "hl7pseudo.dirwatcher.pseudo")
data class DirectoryWatcherProperties(
    val inputDir: String,
    val moveToDir: String,
    val outputDir: String,
) {
    val inputPath: File get() = Path.of(inputDir).toFile()
    val moveToPath: File get() = Path.of(moveToDir).toFile()
    val outputPath: File get() = Path.of(outputDir).toFile()
    val changeFilenameToMsgId: Boolean get() = true
}