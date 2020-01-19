/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package sandbox.explorer

import arrow.fx.IO
import arrow.fx.extensions.fx
import java.io.File
import java.sql.Connection
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import sandbox.explorer.logic.CsvUserImporter
import sandbox.explorer.logic.GitHubApiCaller

fun main(args: Array<String>) = IO.fx {
    val db = App.connectToDatabase()

    val eitherPeople = ! CsvUserImporter.importUsers.value()
    // println(eitherPeople)

    eitherPeople.map { people ->
        people.map { aPerson ->
            val eitherGitHubInfo = GitHubApiCaller.callApi(aPerson.gitHubUsername).unsafeRunSync()
            eitherGitHubInfo.map { gitHubInfo ->
                val eitherGitHubUserInfo = GitHubUserInfo.deserializeFromJson2(gitHubInfo).unsafeRunSync()

                eitherGitHubUserInfo.map { gitHubUserInfo ->
                    transaction(db) {
                        addLogger(StdOutSqlLogger)

                        GitHubMetric.new {
                            login = gitHubUserInfo.username
                            name = "${aPerson.firstName} ${aPerson.lastName}"
                            publicGistsCount = gitHubUserInfo.publicGistCount
                            publicReposCount = gitHubUserInfo.publicReposCount
                            followersCount = gitHubUserInfo.followersCount
                            followingCount = gitHubUserInfo.followingCount
                            accountCreatedAt = DateTime(2020, 1, 1, 12, 0, 0)
                            person = aPerson
                        }
                    }
                }
                println(eitherGitHubUserInfo)
            }
        }
    }
}.unsafeRunSync()

object App {
    fun connectToDatabase(): Database {
        val filePath = File("db/explorer-db.sqlt").getAbsolutePath()
        val db = Database.connect("jdbc:sqlite:$filePath", "org.sqlite.JDBC")
        db.useNestedTransactions = true
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        return db
    }
}
