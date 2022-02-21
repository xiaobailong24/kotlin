interface Flow<out T>

interface MutableSharedFlow<T>

fun foo(output: ((ScrapingOffOutput) -> Unit)) {
    val (model, events) = <!OVERLOAD_RESOLUTION_AMBIGUITY!>presenter<!>(output) { ScrapingOffPresenter() }
}

abstract class Presenter<Events, Model, Output>(outputExtraBufferCapacity: Int = 16) {
    abstract fun Present(events: Events): Model
}


fun <Event : Any, Model, Output> presenter(
    events: Event,
    output: ((Output) -> Unit)? = null,
    presenterProvider: () -> Presenter<Event, Model, Output>
): Model {
    val presenter = presenterProvider()
    return presenter.Present(events)
}

fun <Event : Any, Model, Output> presenter(
    output: ((Output) -> Unit)? = null,
    extraBufferCapacity: Int = 16,
    presenterProvider: () -> Presenter<Flow<Event>, Model, Output>
): PresentedData<Model, MutableSharedFlow<Event>> {
    <!UNREACHABLE_CODE!>return<!> TODO()
}

data class PresentedData<M, E>(
    val model: M,
    val events: E,
)

private class ScrapingOffPresenter : Presenter<Flow<ScrapingOffEvent>, ScrapingOffModel, ScrapingOffOutput>() {
    override fun Present(events: Flow<ScrapingOffEvent>): ScrapingOffModel {
        return ScrapingOffModel()
    }
}

class ScrapingOffEvent
class ScrapingOffOutput
class ScrapingOffModel