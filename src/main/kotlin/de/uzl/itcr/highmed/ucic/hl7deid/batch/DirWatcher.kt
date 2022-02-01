package de.uzl.itcr.highmed.ucic.hl7deid.batch

import org.slf4j.Logger
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.io.File
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@EnableScheduling
abstract class DirWatcher(
    private val dirToWatch: File,
    private val asyncJobLauncher: JobLauncher,
) {

    abstract val log: Logger
    private val lock = ReentrantLock()
    private val processedFiles = mutableListOf<File>()

    @Scheduled(fixedDelay = 1000L)
    fun readFiles() = lock.withLock {
        val files = dirToWatch.list()?.let {
            it.map { p -> Path.of(dirToWatch.absolutePath, p).toFile() }
        } ?: return@withLock
        files.forEach { newFile ->
            if (processedFiles.any { it.absolutePath == newFile.absolutePath }) return@forEach
            processedFiles.add(newFile.absoluteFile)
            val (job, params) = configureJob(newFile)
            try {
                val jobExecution = asyncJobLauncher.run(job, params)
                log.debug("Added ${newFile.absolutePath} to the queue: ${jobExecution.status}")
            } catch (e: JobInstanceAlreadyCompleteException) {
                log.info("The job instance is already completed for this filename: ${newFile.absolutePath}")
            }
        }
    }

    abstract fun configureJob(newFile: File): Pair<Job, JobParameters>
}