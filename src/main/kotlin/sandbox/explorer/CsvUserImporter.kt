package sandbox.explorer

import arrow.core.Left
import arrow.core.Right
import arrow.fx.ForIO
import arrow.fx.IO
import arrow.fx.extensions.fx
import arrow.fx.extensions.io.monad.monad
import arrow.fx.handleError
import arrow.mtl.EitherT
import arrow.mtl.extensions.eithert.monad.monad
import com.opencsv.CSVReaderHeaderAware
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileReader

typealias EitherIO<A> = EitherT<ForIO, AppError, A>

object CsvUserImporter {
    private fun readUserData(fileName: String): EitherIO<List<Array<String>>> =
        EitherT(
            IO.fx {
                val csvReader = CSVReaderHeaderAware(FileReader(fileName))
                val records: List<Array<String>> = csvReader.readAll()

                Right(records)
            }.handleError { Left(AppError.CsvImportError) }
        )

    private fun persistUserInfo(userData: List<Array<String>>) =
        EitherT(
            IO.fx {
                val result = transaction {
                    // addLogger(StdOutSqlLogger)

                    userData.map {
                        Person.findOrCreate(
                            emailValue = it[0],
                            firstNameValue = it[1],
                            lastNameValue = it[2],
                            ratingValue = it[3].toInt(),
                            gitHubUsernameValue = it[4]
                        )
                    }
                }
                Right(result)
            }.handleError { Left(AppError.PersonInsertError) }
        )

    val importUsers =
        EitherIO.monad<ForIO, AppError>(IO.monad()).fx.monad {
            val userData = ! readUserData("resources/users.csv")
            val result = ! persistUserInfo(userData)
            result
        }
}
