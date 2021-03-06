package controllers

import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, Controller }
import org.joda.time.DateTime
import io.swagger.annotations.{ Api, ApiOperation, ApiResponse, ApiResponses }

@Api("Health")
class HealthController extends Controller {
  private[this] val startTime = System.currentTimeMillis()

  @ApiOperation(
    value = "Health endpoint",
    notes = "This endpoints shows the API uptime",
    httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Success - Displays API health")))
  def health: Action[AnyContent] = Action {
    val uptimeInMillis = uptime()
    val dateTime = new DateTime(startTime)
    Ok(Json.obj(
      "startTime" -> s"${dateTime.toLocalDate} ${dateTime.toLocalTime}",
      "uptime" -> millisToHoursMinutesDays(uptimeInMillis)))
  }

  private def millisToHoursMinutesDays(millis: Long): String = {
    val days = millis / (1000 * 60 * 60 * 24)
    val hours = millis / (1000 * 60 * 60) % 24
    val minutes = millis / (1000 * 60) % 60
    s"$days days $hours hours $minutes minutes"
  }

  private def uptime(): Long = {
    val uptimeInMillis = System.currentTimeMillis() - startTime
    uptimeInMillis
  }
}