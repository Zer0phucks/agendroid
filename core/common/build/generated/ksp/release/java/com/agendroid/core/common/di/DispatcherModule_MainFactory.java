package com.agendroid.core.common.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata
@QualifierMetadata("com.agendroid.core.common.di.MainDispatcher")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class DispatcherModule_MainFactory implements Factory<CoroutineDispatcher> {
  @Override
  public CoroutineDispatcher get() {
    return main();
  }

  public static DispatcherModule_MainFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CoroutineDispatcher main() {
    return Preconditions.checkNotNullFromProvides(DispatcherModule.INSTANCE.main());
  }

  private static final class InstanceHolder {
    private static final DispatcherModule_MainFactory INSTANCE = new DispatcherModule_MainFactory();
  }
}
