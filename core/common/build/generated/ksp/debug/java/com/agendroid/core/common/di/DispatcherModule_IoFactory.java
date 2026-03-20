package com.agendroid.core.common.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata
@QualifierMetadata("com.agendroid.core.common.di.IoDispatcher")
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
public final class DispatcherModule_IoFactory implements Factory<CoroutineDispatcher> {
  @Override
  public CoroutineDispatcher get() {
    return io();
  }

  public static DispatcherModule_IoFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CoroutineDispatcher io() {
    return Preconditions.checkNotNullFromProvides(DispatcherModule.INSTANCE.io());
  }

  private static final class InstanceHolder {
    private static final DispatcherModule_IoFactory INSTANCE = new DispatcherModule_IoFactory();
  }
}
