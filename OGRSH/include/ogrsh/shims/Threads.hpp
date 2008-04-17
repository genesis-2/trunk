#ifndef __OGRSH_SHIMS_THREADS_HPP__
#define __OGRSH_SHIMS_THREADS_HPP__

#include <pthread.h>

#include "ogrsh/ShimMacros.hpp"

namespace ogrsh
{
	namespace shims
	{
		SHIM_DECL(int, pthread_create, (pthread_t *thread,
			const pthread_attr_t *attr,
			void (*start_routine)(void*), void *arg));

		void startThreadShims();
		void stopThreadShims();
	}
}

#endif
