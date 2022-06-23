package de.uzl.itcr.highmed.ucic.hl7deid.batch

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
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
@Profile("indexfiledirectorywatcher")
class IndexFileDirectoryWatcher(
    @Autowired asyncJobLauncher: JobLauncher,
    @Autowired val indexFileJob: Job,
    @Autowired val watcherProperties: IndexFileDirectoryWatcherProperties
) : DirWatcher(watcherProperties.directoryFile, asyncJobLauncher) {

    override val log: Logger = LoggerFactory.getLogger(IndexFileDirectoryWatcher::class.java)

    @PostConstruct
    fun postConstruct() {
        log.info("Watching directory for indexing: ${watcherProperties.directoryFile}")
    }

    override fun configureJob(newFile: File) = indexFileJob to JobParametersBuilder()
        .addString("filename", newFile.absolutePath)
        .toJobParameters()
}

@ConstructorBinding
@ConfigurationProperties(prefix = "hl7deid.dirwatcher.index")
class IndexFileDirectoryWatcherProperties(
    directory: String,
    val directoryFile: File = Path.of(directory).toFile()
)