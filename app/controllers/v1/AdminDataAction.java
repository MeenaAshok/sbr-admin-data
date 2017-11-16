package controllers.v1;

import akka.actor.ActorSystem;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import net.jodah.failsafe.FailsafeException;
import play.Logger;
import play.libs.concurrent.Futures;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.codahale.metrics.MetricRegistry.name;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static play.mvc.Http.Status.*;

public class AdminDataAction extends play.mvc.Action.Simple {
    private final Logger.ALogger logger = play.Logger.of("application.AdminDataAction");

    private final Meter requestsMeter;
    private final Timer responsesTimer;
    private final HttpExecutionContext ec;
    @Singleton
    @Inject
    public AdminDataAction(MetricRegistry metrics, HttpExecutionContext ec, ActorSystem actorSystem) {
        this.ec = ec;
        this.requestsMeter = metrics.meter("requestsMeter");
        this.responsesTimer = metrics.timer(name(AdminDataAction.class, "responsesTimer"));
    }

    public CompletionStage<Result> call(Http.Context ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace("call: ctx = " + ctx);
        }

        requestsMeter.mark();
        if (ctx.request().accepts("application/json")) {
            final Timer.Context time = responsesTimer.time();
            return timeout(doCall(ctx), 1L, TimeUnit.SECONDS).whenComplete((r, e) -> time.close());
        } else {
            return completedFuture(
                    status(NOT_ACCEPTABLE, "We only accept application/json")
            );
        }
    }

    private CompletionStage<Result> timeout(final CompletionStage<Result> stage, final long delay, final TimeUnit unit) {
        final CompletionStage<Result> timeoutFuture = Futures.timeout(delay, unit).handle((v, e) -> {
            return Results.status(GATEWAY_TIMEOUT, "Service has timed out");
        });
        return stage.applyToEither(timeoutFuture, Function.identity());
    }

    private CompletionStage<Result> doCall(Http.Context ctx) {
        return delegate.call(ctx).handleAsync((result, e) -> {
            if (e != null) {
                if (e instanceof CompletionException) {
                    Throwable completionException = e.getCause();
                    if (completionException instanceof FailsafeException) {
                        logger.error("Circuit breaker is open!", completionException);
                        return Results.status(SERVICE_UNAVAILABLE, "Service has timed out");
                    } else {
                        logger.error("Direct exception " + e.getMessage(), e);
                        return internalServerError();
                    }
                } else {
                    logger.error("Unknown exception " + e.getMessage(), e);
                    return internalServerError();
                }
            } else {
                return result;
            }
        }, ec.current());
    }
}