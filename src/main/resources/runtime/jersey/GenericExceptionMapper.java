package __PACKAGE__.exception;

import __WS_NS__.core.MediaType;
import __WS_NS__.core.Response;
import __WS_NS__.ext.ExceptionMapper;
import __WS_NS__.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger logger = Logger.getLogger(GenericExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception exception) {
        logger.log(Level.SEVERE, "Unhandled exception", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"Internal server error\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
